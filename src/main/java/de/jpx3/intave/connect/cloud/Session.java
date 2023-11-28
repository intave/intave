package de.jpx3.intave.connect.cloud;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.player.trust.TrustFactor;
import de.jpx3.intave.connect.cloud.protocol.*;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;
import de.jpx3.intave.connect.cloud.protocol.packets.ServerboundKeepAlive;
import de.jpx3.intave.connect.cloud.protocol.pipeline.*;
import de.jpx3.intave.executor.IntaveThreadFactory;
import de.jpx3.intave.module.nayoro.Classifier;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.Key;
import java.security.PublicKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;
import static de.jpx3.intave.connect.cloud.protocol.Direction.SERVERBOUND;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class Session {
  private Shard shard;
  private Cloud cloud;
  private Channel channel;
  private ProtocolSpecification protocol = new ProtocolSpecification();
  private final Queue<Packet<Serverbound>> pendingOutgoing = new ArrayDeque<>();
  private final Queue<Packet<Clientbound>> pendingIncoming = new ArrayDeque<>();
  private final List<Consumer<Void>> startupSubscribers = new ArrayList<>();
  private final List<Consumer<Session>> shutdownSubscribers = new ArrayList<>();

  private PublicKey serverPublicKey;
  private String encryptionAlgorithm;
  private String encryptionScheme;
  private Key primaryKey;
  private byte[] verifyBytes;
//  private Key aesKey;

  private boolean started;

  private final LongAdder receivedBytes = new LongAdder();
  private final LongAdder sentBytes = new LongAdder();

  public Session(Shard shard, Cloud cloud) {
    this.shard = shard;
    this.cloud = cloud;
  }

  public void init(Consumer<Boolean> onFinal) {
    EventLoopGroup group = new NioEventLoopGroup(2, IntaveThreadFactory.ofPriority(3));
    Bootstrap bootstrap = new Bootstrap()
      .group(group)
      .channel(NioSocketChannel.class)
      .option(CONNECT_TIMEOUT_MILLIS, 5000)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
          ch.pipeline()
            .addLast("timeout", new ReadTimeoutHandler(120))
//            .addLast("logger", new LoggingHandler(LogLevel.INFO))
            .addLast("decompression", new Decompression(256))
            .addLast("compression", new Compression(256))
            .addLast("codec", new PacketCodec(protocol, CLIENTBOUND))
            .addLast("processor", new HandshakeReceiver(Session.this))
          ;
        }
      });

    try {
      boolean connected = bootstrap.connect(shard.domain(), shard.port()).await().addListener(future -> {
        if (!future.isSuccess()) {
          onFinal.accept(false);
          return;
        }
        channel = ((ChannelFuture) future).channel();
        channel.closeFuture().addListener(future2 -> {
          IntaveLogger.logger().info("Cloud connection closed forcefully");
          shutdownSubscribers.forEach(subscriber -> subscriber.accept(this));
          group.shutdownGracefully();
          onFinal.accept(false);
        });
        onFinal.accept(true);
      }).await(10, SECONDS);
      if (!connected) {
        IntaveLogger.logger().info("Cloud connection timed out");
        onFinal.accept(false);
      }
    } catch (Exception e) {
      IntaveLogger.logger().info("Cloud disconnected");
      onFinal.accept(false);
    }
  }

  public void keepAliveTick() {
    if (canSend(ServerboundKeepAlive.class)) {
      send(new ServerboundKeepAlive());
    }
  }

  public boolean active() {
    return channel != null && channel.isActive();
  }

  public void reset() {
    shard = null;
    cloud = null;
    channel = null;
    protocol = new ProtocolSpecification();
    pendingIncoming.clear();
    pendingOutgoing.clear();
  }

  public void send(Packet<Serverbound> packet) {
    if (channel == null || !channel.isActive()) {
      pendingOutgoing.add(packet);
      return;
    }
    while (!pendingOutgoing.isEmpty()) {
      channel.writeAndFlush(pendingOutgoing.poll());
    }
    channel.writeAndFlush(packet);
  }

  public long sentBytes() {
    return sentBytes.longValue();
  }

  public long receivedBytes() {
    return receivedBytes.longValue();
  }

  public void serveTrustfactorRequest(Identity id, TrustFactor trustFactor) {
    cloud.serveTrustfactorRequest(id, trustFactor);
  }

  public void serveStorageRequest(Identity id, ByteBuffer buffer) {
    cloud.serveStorageRequest(id, buffer);
  }

  public void serverUploadPlayerLogsRequest(Identity id, int nonce, String logId) {
    cloud.serveUploadPlayerLogs(id, nonce, logId);
  }

  public void serveSampleTransmissionRequest(Identity id, boolean allowed, Classifier classifier) {
    cloud.serveSampleTransmissionRequest(id, allowed, classifier);
  }

  public void onShardsAddition(List<? extends Shard> shards) {
    shards.forEach(shard -> cloud.openSession(shard));;
  }

  public void receivePacketLater(Packet<Clientbound> packet) {
    pendingIncoming.add(packet);
  }

  public Queue<Packet<Clientbound>> pendingIncoming() {
    return pendingIncoming;
  }

  public synchronized void setEncryption(
    Cipher downwardDecryption,
    Cipher upwardEncryption
  ) {
    ChannelPipeline pipeline = channel.pipeline();
    ChannelHandler current = pipeline.get("encryption");

    Encryption encryption = new Encryption(upwardEncryption, sentBytes);
    Decryption decryption = new Decryption(downwardDecryption, receivedBytes);

    if (current == null) {
      pipeline.addAfter("timeout", "encryption", encryption);
      pipeline.addAfter("timeout","decryption", decryption);

      pipeline.addAfter("decryption", "accumulator", new Accumulator());
      pipeline.addAfter("encryption", "prepender", new Prepender());
    } else {
      pipeline.replace("encryption", "encryption", encryption);
      pipeline.replace("decryption", "decryption", decryption);
    }
  }

  public void setProcessor(ChannelHandler handler) {
    pipeline().replace("processor", "processor", handler);
  }

  public ChannelPipeline pipeline() {
    return channel.pipeline();
  }

  public Shard shard() {
    return shard;
  }

  public void close() {
    if (channel != null) {
      channel.close();
    }
  }

  public boolean canSend(Packet<Serverbound> packet) {
    return channel != null && channel.isActive() &&
      protocol.packetAvailable(SERVERBOUND, packet.name());
  }

  public boolean canSend(Class<? extends Packet<Serverbound>> packetClass) {
    return channel != null && channel.isActive() &&
      protocol.packetAvailable(SERVERBOUND, PacketRegistry.serverboundName(packetClass));
  }

  public void subscribeToStarted(Consumer<Void> consumer) {
    if (started) {
      consumer.accept(null);
    } else {
      startupSubscribers.add(consumer);
    }
  }

  public void markStarted() {
    started = true;
    startupSubscribers.forEach(subscriber -> subscriber.accept(null));
    startupSubscribers.clear();
  }

  public void subscribeToShutdown(Consumer<Session> consumer) {
    shutdownSubscribers.add(consumer);
  }

  public ProtocolSpecification protocol() {
    return protocol;
  }

  public PublicKey serverPublicKey() {
    return serverPublicKey;
  }

  public void setServerPublicKey(PublicKey serverPublicKey) {
    this.serverPublicKey = serverPublicKey;
  }

  public String encryptionAlgorithm() {
    return encryptionAlgorithm;
  }

  public void setEncryptionAlgorithm(String encryptionAlgorithm) {
    this.encryptionAlgorithm = encryptionAlgorithm;
  }

  public String encryptionScheme() {
    return encryptionScheme;
  }

  public void setEncryptionScheme(String encryptionScheme) {
    this.encryptionScheme = encryptionScheme;
  }

  public Key primaryKey() {
    return primaryKey;
  }

  public void setPrimaryKey(Key aesKey) {
    this.primaryKey = aesKey;
  }

  public byte[] verifyBytes() {
    return verifyBytes;
  }

  public void setVerifyBytes(byte[] verifyBytes) {
    this.verifyBytes = verifyBytes;
  }
}

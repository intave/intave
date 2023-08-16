package de.jpx3.intave.module.cloud;

import de.jpx3.intave.module.cloud.protocol.Packet;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.module.cloud.protocol.listener.Serverbound;
import de.jpx3.intave.module.cloud.protocol.pipeline.Compression;
import de.jpx3.intave.module.cloud.protocol.pipeline.Decompression;
import de.jpx3.intave.module.cloud.protocol.pipeline.HandshakeReceiver;
import de.jpx3.intave.module.cloud.protocol.pipeline.PacketCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.security.Key;
import java.util.ArrayDeque;
import java.util.Queue;

import static de.jpx3.intave.module.cloud.protocol.Direction.CLIENTBOUND;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class Session {
  private Cloud cloud;
  private Channel channel;
  private final Queue<Packet<Serverbound>> pendingOutgoing = new ArrayDeque<>();
  private final Queue<Packet<Clientbound>> pendingIncoming = new ArrayDeque<>();

  private Key rsaKey;
  private Key aesKey;

  public Session(Cloud cloud) {
    this.cloud = cloud;
  }

  public void init() {
    EventLoopGroup group = new NioEventLoopGroup();
    Bootstrap bootstrap = new Bootstrap()
      .group(group)
      .channel(NioSocketChannel.class)
      .handler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
          ch.pipeline()
            .addLast("timeout", new ReadTimeoutHandler(30))
            .addLast("decompression", new Decompression(256))
            .addLast("compression", new Compression(256))
            .addLast("codec", new PacketCodec(CLIENTBOUND))
            .addLast("processor", new HandshakeReceiver(Session.this))
          ;
        }
      });

    try {
      boolean connected = bootstrap.connect("service.intave.ac", 2024).addListener(future -> {
        if (!future.isSuccess()) {
          future.cause().printStackTrace();
          return;
        }
        channel = ((ChannelFuture) future).channel();
      }).await(10, SECONDS);
      if (!connected) {
        System.out.println("Failed to connect to service.intave.ac:2024");
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    channel.closeFuture().addListener(future -> {
      System.out.println("Connection closed");
      group.shutdownGracefully();
    });
  }

  public void sendPacket(Packet<Serverbound> packet) {
    if (channel == null || !channel.isActive()) {
      pendingOutgoing.add(packet);
      return;
    }
    while (!pendingOutgoing.isEmpty()) {
      channel.writeAndFlush(pendingOutgoing.poll());
    }
    channel.writeAndFlush(packet);
  }

  public void receivePacketLater(Packet<Clientbound> packet) {
    pendingIncoming.add(packet);
  }

  public Queue<Packet<Clientbound>> pendingIncoming() {
    return pendingIncoming;
  }

  public void setProcessor(ChannelHandler handler) {
    pipeline().replace("processor", "processor", handler);
  }

  public ChannelPipeline pipeline() {
    return channel.pipeline();
  }

  public void close() {
    channel.close();
  }
}

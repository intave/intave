package de.jpx3.intave.connect.cloud.protocol.pipeline;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.connect.cloud.Session;
import de.jpx3.intave.connect.cloud.protocol.*;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.connect.cloud.protocol.packets.ClientboundHello;
import de.jpx3.intave.connect.cloud.protocol.packets.ServerboundConfirmEncryption;
import de.jpx3.intave.connect.cloud.protocol.packets.ServerboundHello;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;
import static de.jpx3.intave.connect.cloud.protocol.Direction.SERVERBOUND;

public final class HandshakeReceiver extends ChannelInboundHandlerAdapter implements Clientbound {
  private final Session session;

  public HandshakeReceiver(Session session) {
    this.session = session;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    Shard shard = session.shard();
    ServerboundHello serverHelloPacket = ServerboundHello.builder()
      .token(shard == null ? new Token(new byte[0], 0) : shard.token())
      .supportedEncryptionAlgorithms(Security.getAlgorithms("Cipher").stream().filter(s -> s.startsWith("AES")).collect(Collectors.toList()))
      .supportedEncryptionKeySizes(Collections.singletonList(128))
      .supportedCompressionAlgorithms(Collections.singletonList("GZIP"))
      .supportedHMACAlgorithms(new ArrayList<>(Security.getAlgorithms("Mac")))
      .clientboundProtocol(PacketRegistry.packetSpecsFor(CLIENTBOUND))
      .serverboundProtocol(PacketRegistry.packetSpecsFor(SERVERBOUND))
      .build();
    ctx.writeAndFlush(serverHelloPacket);
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object object) {
    Packet<?> packet = (Packet<?>) object;
    if (!(packet instanceof ClientboundHello)) {
      //noinspection unchecked
      session.receivePacketLater((Packet<Clientbound>) packet);
      return;
    }
    //noinspection unchecked
    ((Packet<Clientbound>) packet).accept(this);
    ctx.writeAndFlush(buildConfirmEncryptionPacket()).addListener(future -> {
      String algorithm = session.encryptionScheme();
      // use AES with key from packet
      Cipher downDecryptCipher = Cipher.getInstance(algorithm);
      Cipher upEncryptCipher = Cipher.getInstance(algorithm);

      byte[] iv = session.verifyBytes();
      downDecryptCipher.init(Cipher.DECRYPT_MODE, session.primaryKey(), new IvParameterSpec(iv));
      upEncryptCipher.init(Cipher.ENCRYPT_MODE, session.primaryKey(), new IvParameterSpec(iv));
      session.setEncryption(downDecryptCipher, upEncryptCipher);

      StandardClientRetriever processor = new StandardClientRetriever(session);
      session.setProcessor(processor);
      session.markStarted();

      session.pendingIncoming().forEach(
        clientboundPacket -> clientboundPacket.accept(processor)
      );
    });
  }

  @Override
  public void onClientHello(ClientboundHello packet) {
    ProtocolSpecification protocol = session.protocol();
    protocol.overrideAvailablePackets(CLIENTBOUND, new HashSet<>(packet.clientboundPackets()));
    protocol.overrideAvailablePackets(SERVERBOUND, new HashSet<>(packet.serverboundPackets()));
    protocol.overridePacketIds(CLIENTBOUND, packet.clientboundPackets());
    protocol.overridePacketIds(SERVERBOUND, packet.serverboundPackets());

    String encryption = packet.encryptionAlgorithm();
    session.setEncryptionScheme(encryption);
    if (encryption.contains("/")) {
      encryption = encryption.substring(0, encryption.indexOf("/"));
    }
    session.setEncryptionAlgorithm(encryption);
    session.setServerPublicKey(packet.publicKey());
    try {
      Key generatedKey = generateKey(session.encryptionAlgorithm(), 128);
      session.setPrimaryKey(generatedKey);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
      session.close();
    }
    session.setVerifyBytes(packet.verifyToken());
  }

  private ServerboundConfirmEncryption buildConfirmEncryptionPacket() {
    byte[] sharedSecretEncrypted = encryptRSAChunked(session.primaryKey().getEncoded());
    byte[] verifyBytesEncrypted = encryptRSAChunked(session.verifyBytes());
    return new ServerboundConfirmEncryption(sharedSecretEncrypted, verifyBytesEncrypted);
  }

  private byte[] encryptRSAChunked(byte[] bytes) {
    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, session.serverPublicKey());
      return cipher.doFinal(bytes);
    } catch (Exception e) {
      session.close();
      throw new RuntimeException(e);
    }
  }

  private static Key generateKey(String cipher, int keySize) throws NoSuchAlgorithmException {
    if (cipher.contains("/")) {
      cipher = cipher.substring(0, cipher.indexOf("/"));
    }
    KeyGenerator keyGenerator = KeyGenerator.getInstance(cipher);
    keyGenerator.init(keySize);
    return keyGenerator.generateKey();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
//    channelHandlerContext.fireExceptionCaught(throwable);
//    throwable.printStackTrace();
    IntaveLogger.logger().info("Exception caught in cloud connection " + channelHandlerContext.name() + ": " + throwable.getMessage());
    channelHandlerContext.close();
  }
}

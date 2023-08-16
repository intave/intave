package de.jpx3.intave.module.cloud.protocol.pipeline;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.cloud.Session;
import de.jpx3.intave.module.cloud.protocol.Packet;
import de.jpx3.intave.module.cloud.protocol.PacketRegistry;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.module.cloud.protocol.packets.ClientboundHelloPacket;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundConfirmEncryptionPacket;
import de.jpx3.intave.module.cloud.protocol.packets.ServerboundHelloPacket;
import de.jpx3.intave.security.HWIDVerification;
import de.jpx3.intave.security.HashAccess;
import de.jpx3.intave.security.LicenseAccess;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.File;
import java.net.URISyntaxException;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static de.jpx3.intave.module.cloud.protocol.Direction.CLIENTBOUND;
import static de.jpx3.intave.module.cloud.protocol.Direction.SERVERBOUND;

public final class HandshakeReceiver extends ChannelInboundHandlerAdapter implements Clientbound {
  private final Session session;

  public HandshakeReceiver(Session session) {
    this.session = session;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    ServerboundHelloPacket serverHelloPacket = ServerboundHelloPacket.builder()
      .identifierHead(LicenseAccess.rawLicense().substring(0, 8))
      .identifierChecksum(LicenseAccess.rawLicense().split("IIIII")[1])
      .jarFileHash(HashAccess.hashOf(jarFile()))
      .hwid(HWIDVerification.publicHardwareIdentifier())
      .gameId(IntavePlugin.gameId().toString())
      .supportedEncryptionAlgorithms(Security.getAlgorithms("Cipher").stream().filter(s -> s.startsWith("AES")).collect(Collectors.toList()))
      .supportedEncryptionKeySizes(Collections.singletonList(256))
      .supportedCompressionAlgorithms(Collections.singletonList("GZIP"))
      .supportedHMACAlgorithms(new ArrayList<>(Security.getAlgorithms("Mac")))
      .clientboundProtocol(PacketRegistry.specificationFor(CLIENTBOUND))
      .serverboundProtocol(PacketRegistry.specificationFor(SERVERBOUND))
      .build();
    ctx.writeAndFlush(serverHelloPacket);
  }

  private File jarFile() {
    try {
      return new File(HandshakeReceiver.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object object) {
    Packet<?> packet = (Packet<?>) object;
    if (!(packet instanceof ClientboundHelloPacket)) {
      //noinspection unchecked
      session.receivePacketLater((Packet<Clientbound>) packet);
      return;
    }

    //noinspection unchecked
    ((Packet<Clientbound>) packet).accept(this);
    ctx.writeAndFlush(new ServerboundConfirmEncryptionPacket()).addListener(future -> {
      session.setProcessor(new StandardPacketReceiver(session));
    });
  }

  @Override
  public void onClientHello(ClientboundHelloPacket packet) {
    PacketRegistry.enterIdAssignment(CLIENTBOUND, packet.clientboundPackets());
    PacketRegistry.enterIdAssignment(SERVERBOUND, packet.serverboundPackets());
  }
}

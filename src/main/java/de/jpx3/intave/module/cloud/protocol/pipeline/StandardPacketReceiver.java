package de.jpx3.intave.module.cloud.protocol.pipeline;

import de.jpx3.intave.module.cloud.Session;
import de.jpx3.intave.module.cloud.protocol.Packet;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;
import de.jpx3.intave.module.cloud.protocol.packets.ClientboundCloseConnectionPacket;
import de.jpx3.intave.module.cloud.protocol.packets.ClientboundHelloPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import static de.jpx3.intave.module.cloud.protocol.Direction.CLIENTBOUND;

public final class StandardPacketReceiver extends ChannelInboundHandlerAdapter implements Clientbound {
  private Session session;

  public StandardPacketReceiver(Session session) {
    this.session = session;
  }

  @Override
  public void channelRead(ChannelHandlerContext channelHandlerContext, Object o) throws Exception {
    if (o instanceof Packet) {
      Packet<?> packet = (Packet<?>) o;
      if (packet.direction() == CLIENTBOUND) {
        onAny(packet);
      }
    }
  }

  @Override
  public void onCloseConnection(ClientboundCloseConnectionPacket packet) {
    System.out.println("Connection closed: " + packet.reason());
    session.close();
  }

  @Override
  public void onClientHello(ClientboundHelloPacket packet) {
    throw new RuntimeException("Unexpected packet " + packet.name());
  }

}

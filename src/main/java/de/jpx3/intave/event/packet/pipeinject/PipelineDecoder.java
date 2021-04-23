package de.jpx3.intave.event.packet.pipeinject;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PipelineDecoder extends ChannelDuplexHandler {
  private final UUID id;
  private final InjectionService injectionService;

  public PipelineDecoder(UUID id, InjectionService injectionService) {
    this.id = id;
    this.injectionService = injectionService;
  }

  @Override
  public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
//    System.out.println(PacketType.fromClass(packet.getClass()));
//    PacketType packetType = PacketType.fromClass(packet.getClass());
//    Collection<LocalPacketAdapter> adapters = injectionService.subscriptionsOf(packetType);
//    boolean cancelled = false;
//    if(adapters != null) {
//      System.out.println(packet);
//
//      PacketContainer packetContainer = PacketContainer.fromPacket(packet);
//      PacketEvent packetEvent = PacketEvent.fromClient(packet, packetContainer, player());
//      for (LocalPacketAdapter adapter : adapters) {
//        adapter.onPacketReceiving(packetEvent);
//      }
//      cancelled = packetEvent.isCancelled();
//    }
//    if(!cancelled) {
//    } else {
//      context.fireChannelReadComplete();
//    }
    context.fireChannelRead(packet);
  }

  public Player player() {
    return Bukkit.getPlayer(id);
  }

  @Override
  public String toString() {
    return "IntavePipelineDecoder";
  }
}

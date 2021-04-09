package de.jpx3.intave.event.packet.pipeinject;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.event.packet.LocalPacketAdapter;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public final class v8PipelineHandler extends ChannelDuplexHandler {
  private final UUID id;
  private final InjectionService injectionService;

  public v8PipelineHandler(UUID id, InjectionService injectionService) {
    this.id = id;
    this.injectionService = injectionService;
  }

  @Override
  public void channelRead(ChannelHandlerContext context, Object packet) throws Exception {
    PacketType packetType = PacketType.fromClass(packet.getClass());
    Collection<LocalPacketAdapter> adapters = injectionService.subscriptionsOf(packetType);
    boolean cancelled = false;
    if(adapters != null) {
      PacketContainer packetContainer = PacketContainer.fromPacket(packet);
      PacketEvent packetEvent = PacketEvent.fromClient(packet, packetContainer, player());
      for (LocalPacketAdapter adapter : adapters) {
        adapter.onPacketReceiving(packetEvent);
      }
      cancelled = packetEvent.isCancelled();
    }
    if(!cancelled) {
      super.channelRead(context, packet);
    }
  }

  @Override
  public void write(ChannelHandlerContext channelHandlerContext, Object packet, ChannelPromise channelPromise) throws Exception {
    PacketType packetType = PacketType.fromClass(packet.getClass());
    Collection<LocalPacketAdapter> adapters = injectionService.subscriptionsOf(packetType);
    boolean cancelled = false;
    if(adapters != null) {
      PacketContainer packetContainer = PacketContainer.fromPacket(packet);
      PacketEvent packetEvent = PacketEvent.fromClient(packet, packetContainer, player());
      for (LocalPacketAdapter adapter : adapters) {
        adapter.onPacketReceiving(packetEvent);
      }
      cancelled = packetEvent.isCancelled();
    }
    if(!cancelled) {
      super.write(channelHandlerContext, packet, channelPromise);
    }
  }

  public Player player() {
    return Bukkit.getPlayer(id);
  }
}

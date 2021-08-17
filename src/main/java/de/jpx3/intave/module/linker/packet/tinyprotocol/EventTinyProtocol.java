package de.jpx3.intave.module.linker.packet.tinyprotocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.module.linker.packet.FilteringPacketAdapter;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.util.Collection;

@DoNotFlowObfuscate
public final class EventTinyProtocol extends TinyProtocol {
  private final InjectionService injectionService;

  public EventTinyProtocol(IntavePlugin plugin, InjectionService injectionService) {
    super(plugin);
    this.injectionService = injectionService;
  }

  @Override
  public Object onPacketOutAsync(Player receiver, Channel channel, Object packet) {
    PacketType packetType = PacketRegistry.getPacketType(packet.getClass());
    if (packetType != null) {
      Collection<FilteringPacketAdapter> subscriptions = injectionService.subscriptionsOf(packetType);
      if (subscriptions != null && !subscriptions.isEmpty()) {
        PacketEvent packetEvent = PacketEvent.fromServer(packet, PacketContainer.fromPacket(packet), receiver);
        subscriptions.forEach(subscription -> subscription.onPacketSending(packetEvent));
        packet = packetEvent.getPacket().getHandle();
        if (packetEvent.isCancelled()) {
          return null;
        }
      }
    }
    return super.onPacketOutAsync(receiver, channel, packet);
  }
}

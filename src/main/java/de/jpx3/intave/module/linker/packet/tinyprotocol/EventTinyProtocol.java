package de.jpx3.intave.module.linker.packet.tinyprotocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.packet.PacketRegistry;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.module.linker.packet.FilteringPacketAdapter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.util.Collection;

final class EventTinyProtocol extends TinyProtocol {
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
        // There will not be a user on login packet send, so the filtering adapters resolve it themselves
        User user = packetType != PacketType.Play.Server.LOGIN && receiver != null ? UserRepository.userOf(receiver) : null;
        subscriptions.forEach(subscription -> subscription.onPacketSending(packetEvent, user));
        packet = packetEvent.getPacket().getHandle();
        if (packetEvent.isCancelled()) {
          return null;
        }
      }
    }
    return super.onPacketOutAsync(receiver, channel, packet);
  }
}

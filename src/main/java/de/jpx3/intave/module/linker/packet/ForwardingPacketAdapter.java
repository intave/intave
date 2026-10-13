package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;

public final class ForwardingPacketAdapter extends WeakReferencePacketAdapter {
  private static final boolean TEMP_PLAYER_CHECK;

  static {
    TEMP_PLAYER_CHECK = Arrays.stream(PacketEvent.class.getMethods())
      .anyMatch(method -> method.getName().equalsIgnoreCase("isPlayerTemporary"));
  }

  private final Collection<FilteringPacketAdapter> targetList;

  public ForwardingPacketAdapter(
    IntavePlugin plugin,
    PacketType packetType,
    Collection<FilteringPacketAdapter> targetList
  ) {
    super(plugin, ListenerPriority.LOWEST, new PacketType[]{packetType}, ALLOW_ASYNC_SENDING);
    this.targetList = targetList;
  }

  @Override
  public void onPacketSending(PacketEvent event) {
    if (TEMP_PLAYER_CHECK) {
      // perform temporary check
      if (event.isPlayerTemporary()) {
        return;
      }
    }
    Player player = event.getPlayer();
    if (player == null) {
      return;
    }
    // The user is registered while the login packet is being sent, so the filtering adapters resolve it themselves
    if (event.getPacketType() == PacketType.Play.Server.LOGIN) {
      for (FilteringPacketAdapter filteringPacketAdapter : targetList) {
        filteringPacketAdapter.onPacketSending(event);
      }
      return;
    }
    User user = UserRepository.userOf(player);
    if (user.shouldIgnoreNextOutboundPacket()) {
//    user.receiveNextOutboundPacketAgain();
      return;
    }
    for (FilteringPacketAdapter filteringPacketAdapter : targetList) {
      filteringPacketAdapter.onPacketSending(event, user);
    }
  }

  @Override
  public void onPacketReceiving(PacketEvent event) {
    if (TEMP_PLAYER_CHECK) {
      // perform temporary check
      if (event.isPlayerTemporary()) {
//          Timings.packetProcessing.stop();
        return;
      }
    }
    User user = UserRepository.userOf(event.getPlayer());
    if (user.shouldIgnoreNextInboundPacket()) {
      user.receiveNextInboundPacketAgain();
      return;
    }
    for (FilteringPacketAdapter filteringPacketAdapter : targetList) {
      filteringPacketAdapter.onPacketReceiving(event, user);
    }
  }

  @Override
  public String toString() {
    return targetList.toString();
  }
}

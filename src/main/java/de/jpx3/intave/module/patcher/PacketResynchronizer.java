package de.jpx3.intave.module.patcher;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.diagnostic.PacketSynchronizations;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketResynchronizer extends Module {
  /**
   * No PacketEvents twin exists for this subscription, and none can: the module is a repair for a
   * ProtocolLib specific dispatch quirk, not a packet reader.
   * <p>
   * ProtocolLib fires an outbound listener on whichever thread wrote the packet, which is the main
   * thread for most server code and a Netty event loop thread for the rest. This module catches the
   * second case - {@link #isInInvalidThread()} matches on the {@code "Netty "} thread name prefix
   * Spigot gives its IO threads - cancels the packet and replays it once the user's checks are
   * synchronised again, so a check never observes a send from off thread.
   * <p>
   * PacketEvents has no such split. It is a Netty pipeline injector, so <em>every</em> listener call
   * arrives on that same event loop thread by design. The guard would therefore match every single
   * outbound packet, and a twin would cancel and requeue the entire outbound stream rather than the
   * rare off thread write it was written for. Two further pieces of the body are ProtocolLib bound
   * as well: {@link PacketSender#sendServerPacket} replays through the ProtocolLib manager, and
   * {@link PacketSynchronizations} keys its diagnostic counters by ProtocolLib's {@code PacketType}.
   */
  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      ABILITIES_OUT, ATTACH_ENTITY, /*CLOSE_WINDOW*/ ENTITY_DESTROY, ENTITY_LOOK, ENTITY_METADATA,
      ENTITY_MOVE_LOOK, ENTITY_STATUS, ENTITY_TELEPORT, MOUNT, NAMED_ENTITY_SPAWN,
      /*OPEN_WINDOW,*/ PLAYER_INFO, PLAYER_LIST_HEADER_FOOTER, POSITION, REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK,
      REMOVE_ENTITY_EFFECT, RESPAWN, SPAWN_ENTITY, SPAWN_ENTITY_LIVING, /*WINDOW_ITEMS,*/ WORLD_BORDER
    }
  )
  public void catchDesynchronized(PacketEvent event) {
    if (isInInvalidThread()) {
      event.setCancelled(true);
      Player player = event.getPlayer();
      User user = UserRepository.userOf(player);
      PacketContainer packet = event.getPacket();
      Synchronizer.synchronize(user, () -> sendPacket(player, packet));
      PacketSynchronizations.enterResynchronization(event.getPacketType());
    }
  }

  private final Map<String, Boolean> cache = new HashMap<>();

  private boolean isInInvalidThread() {
    return cache.computeIfAbsent(Thread.currentThread().getName(), s -> s.startsWith("Netty "));
  }

  private void sendPacket(Player player, PacketContainer packet) {
    PacketSender.sendServerPacket(player, packet);
  }
}

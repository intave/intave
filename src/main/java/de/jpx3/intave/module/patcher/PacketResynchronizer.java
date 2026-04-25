package de.jpx3.intave.module.patcher;

import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.diagnostic.PacketSynchronizations;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.PacketSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

import static de.jpx3.intave.module.linker.packet.PacketId.Server.*;

public final class PacketResynchronizer extends Module {
  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      ABILITIES_OUT, ATTACH_ENTITY, /*CLOSE_WINDOW*/ ENTITY_DESTROY, ENTITY_LOOK, ENTITY_METADATA,
      ENTITY_MOVE_LOOK, ENTITY_STATUS, ENTITY_TELEPORT, MOUNT, NAMED_ENTITY_SPAWN,
      /*OPEN_WINDOW,*/ PLAYER_INFO, PLAYER_LIST_HEADER_FOOTER, POSITION, REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK,
      REMOVE_ENTITY_EFFECT, RESPAWN, SPAWN_ENTITY, SPAWN_ENTITY_LIVING, /*WINDOW_ITEMS,*/ WORLD_BORDER
    }
  )
  public void catchDesynchronized(ProtocolPacketEvent event) {
    if (isInInvalidThread()) {
      event.setCancelled(true);
      Player player = event.getPlayer();
      PacketContainer packet = event.getPacket();
      Synchronizer.synchronize(() -> sendPacket(player, packet));
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

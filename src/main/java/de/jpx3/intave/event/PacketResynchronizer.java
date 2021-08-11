package de.jpx3.intave.event;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.detect.EventProcessor;
import de.jpx3.intave.event.packet.ListenerPriority;
import de.jpx3.intave.event.packet.PacketSubscription;
import de.jpx3.intave.tools.sync.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;

import static de.jpx3.intave.event.packet.PacketId.Server.*;

public final class PacketResynchronizer implements EventProcessor {
  private final IntavePlugin plugin;

  public PacketResynchronizer(IntavePlugin plugin) {
    this.plugin = plugin;
    this.setup();
  }

  private void setup() {
    plugin.eventLinker().registerEventsIn(this);
    plugin.packetSubscriptionLinker().linkSubscriptionsIn(this);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsOut = {
      ATTACH_ENTITY, CLOSE_WINDOW, ENTITY_DESTROY, ENTITY_LOOK, ENTITY_METADATA,
      ENTITY_MOVE_LOOK, ENTITY_STATUS, ENTITY_TELEPORT, MOUNT, NAMED_ENTITY_SPAWN,
      OPEN_WINDOW, REL_ENTITY_MOVE, REL_ENTITY_MOVE_LOOK, RESPAWN, SPAWN_ENTITY,
      SPAWN_ENTITY_LIVING, REMOVE_ENTITY_EFFECT, POSITION
    }
  )
  public void catchDesynchronized(PacketEvent event) {
    if (!Bukkit.isPrimaryThread()) {
      event.setCancelled(true);
      Player player = event.getPlayer();
      PacketContainer packet = event.getPacket();
      Synchronizer.synchronize(() -> {
        try {
          ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (InvocationTargetException exception) {
          exception.printStackTrace();
        }
      });
    }
  }
}

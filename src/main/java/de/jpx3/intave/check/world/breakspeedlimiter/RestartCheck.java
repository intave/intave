package de.jpx3.intave.check.world.breakspeedlimiter;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class RestartCheck extends MetaCheckPart<BreakSpeedLimiter, RestartCheck.BreakSpeedStartMeta> {
  public RestartCheck(BreakSpeedLimiter parentCheck) {
    super(parentCheck, RestartCheck.BreakSpeedStartMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      POSITION, POSITION_LOOK, LOOK, FLYING, VEHICLE_MOVE
    }
  )
  public void tickUpdate(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    BreakSpeedStartMeta meta = metaOf(user);
    meta.ticks++;
  }

  @PacketSubscription(
    priority = ListenerPriority.LOWEST,
    packetsIn = {
      BLOCK_DIG
    }
  )
  public void receiveBlockAction(ProtocolPacketEvent event, WrapperPlayClientPlayerDigging packet) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RestartCheck.BreakSpeedStartMeta meta = metaOf(user);
    ProtocolMetadata clientData = user.meta().protocol();

    DiggingAction digType = packet.getAction();

    switch (digType) {
      case START_DIGGING: {
        if (clientData.flyingPacketsAreSent()) {
          int ticksBetween = meta.ticks - meta.blockBreakTick;
          if (ticksBetween < 5) {
            String message = "started breaking too quickly";
            String details = (ticksBetween == 1 ? "one tick" : ticksBetween + " ticks");
            ViolationProcessor violationProcessor = Modules.violationProcessor();
            Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
              .forPlayer(player).withMessage(message).withDetails(details).withVL(5)
              .build();
            ViolationContext violationContext = violationProcessor.processViolation(violation);
            if (violationContext.shouldCounterThreat()) {
              event.setCancelled(true);
              meta.cancelNextStop = true;
            }
          }
        } else {
          long milliseconds = System.currentTimeMillis() - meta.blockBreakTimestamp;
          if (milliseconds < 200) {
            if (meta.blockBreakStartVL++ > 5) {
              String message = "started breaking too quickly";
              String details = milliseconds + "ms between";
              ViolationProcessor violationProcessor = Modules.violationProcessor();
              Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
                .forPlayer(player).withMessage(message).withDetails(details)
                .withVL(1).build();
              ViolationContext violationContext = violationProcessor.processViolation(violation);
              if (violationContext.shouldCounterThreat()) {
                event.setCancelled(true);
                meta.cancelNextStop = true;
              }
              meta.blockBreakStartVL--;
            }
          } else if (meta.blockBreakStartVL > 0) {
            meta.blockBreakStartVL -= 0.4;
          }
        }
        break;
      }
      case FINISHED_DIGGING: {
        meta.blockBreakTick = meta.ticks;
        meta.blockBreakTimestamp = System.currentTimeMillis();
        if (meta.cancelNextStop) {
          meta.cancelNextStop = false;
          event.setCancelled(true);
//          BlockPosition blockPosition = packet.getBlockPositionModifier().read(0);
          BlockPosition blockPosition = blockPositionOf(packet.getBlockPosition());
          refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
        }
        break;
      }
    }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Synchronizer.synchronize(() -> {
      player.updateInventory();
      refreshBlock(player, targetLocation);
//      for (EnumDirection direction : EnumDirection.values()) {
//        Location placedBlock = targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
//        refreshBlock(player, placedBlock);
//      }
    });
  }

  private void refreshBlock(Player player, Location location) {
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
      return;
    }
    Block block = VolatileBlockAccess.blockAccess(location);
    Vector3i position = new Vector3i(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(position, SpigotConversionUtil.fromBukkitMaterialData(block.getState().getData()));
    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
  }

  private BlockPosition blockPositionOf(Vector3i vector) {
    return new BlockPosition(vector.x, vector.y, vector.z);
  }

  public static final class BreakSpeedStartMeta extends CheckCustomMetadata {
    private int ticks;
    private int blockBreakTick;
    private long blockBreakTimestamp;
    private double blockBreakStartVL;
    private boolean cancelNextStop;
  }
}

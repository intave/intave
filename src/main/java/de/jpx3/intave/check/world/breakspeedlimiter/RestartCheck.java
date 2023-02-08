package de.jpx3.intave.check.world.breakspeedlimiter;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.world.BreakSpeedLimiter;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.module.violation.ViolationProcessor;
import de.jpx3.intave.packet.PacketSender;
import de.jpx3.intave.packet.converter.BlockPositionConverter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

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
  public void tickUpdate(PacketEvent event) {
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
  public void receiveBlockAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RestartCheck.BreakSpeedStartMeta meta = metaOf(user);
    ProtocolMetadata clientData = user.meta().protocol();

    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

    switch (digType) {
      case START_DESTROY_BLOCK: {
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
            meta.blockBreakStartVL -= 0.2;
          }
        }
        break;
      }
      case STOP_DESTROY_BLOCK: {
        meta.blockBreakTick = meta.ticks;
        meta.blockBreakTimestamp = System.currentTimeMillis();
        if (meta.cancelNextStop) {
          meta.cancelNextStop = false;
          event.setCancelled(true);
//          BlockPosition blockPosition = packet.getBlockPositionModifier().read(0);
          BlockPosition blockPosition = event.getPacket().getModifier()
            .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
            .read(0);
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
    PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.BLOCK_CHANGE);
    if (!VolatileBlockAccess.isInLoadedChunk(location.getWorld(), location.getBlockX(), location.getBlockZ())) {
      return;
    }
    Block block = VolatileBlockAccess.blockAccess(location);
    Object handle = BlockVariantNativeAccess.nativeVariantAccess(block);
    WrappedBlockData blockData = WrappedBlockData.fromHandle(handle);
    packet.getBlockData().write(0, blockData);

    BlockPosition position = new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    packet.getBlockPositionModifier().write(0, position);
    PacketSender.sendServerPacket(player, packet);
  }

  public static final class BreakSpeedStartMeta extends CheckCustomMetadata {
    private int ticks;
    private int blockBreakTick;
    private long blockBreakTimestamp;
    private double blockBreakStartVL;
    private boolean cancelNextStop;
  }
}
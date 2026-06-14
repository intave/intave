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
import de.jpx3.intave.math.MathHelper;
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
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class RestartCheck extends MetaCheckPart<BreakSpeedLimiter, RestartCheck.BreakSpeedStartMeta> {
  private static final long CLOSE_ENOUGH_RESTART_DELAY_MILLIS = 275L;
  private static final long EXPECTED_RESTART_DELAY_MILLIS = 300L;
  private static final double MAX_STORED_RESTART_ADVANTAGE_MILLIS = 1000.0D;

  public RestartCheck(BreakSpeedLimiter parentCheck) {
    super(parentCheck, RestartCheck.BreakSpeedStartMeta.class);
  }

  @PacketSubscription(priority = ListenerPriority.LOWEST, packetsIn = {
      BLOCK_DIG
  })
  public void receiveBlockAction(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    RestartCheck.BreakSpeedStartMeta meta = metaOf(user);

    PacketContainer packet = event.getPacket();
    EnumWrappers.PlayerDigType digType = packet.getPlayerDigTypes().read(0);

    switch (digType) {
      case START_DESTROY_BLOCK: {
        BlockPosition blockPosition = event.getPacket().getModifier()
            .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
            .read(0);
        if (isRepeatedActiveStart(meta.breakProcess, meta.targetBlockPosition, blockPosition)) {
          return;
        }

        long milliseconds = System.currentTimeMillis() - meta.blockBreakTimestamp;
        RestartAssessment assessment = assessRestartDelay(milliseconds, meta.blockBreakStartVL, user.latency());
        meta.blockBreakStartVL = assessment.balance();

        if (shouldReportRestart(assessment, meta.blockBreakTimestamp, meta.restartFlagBreakTimestamp)) {
          String message = "started breaking too quickly";
          String details = milliseconds + "ms between";
          ViolationProcessor violationProcessor = Modules.violationProcessor();
          Violation violation = Violation.builderFor(BreakSpeedLimiter.class)
              .forPlayer(player).withMessage(message).withDetails(details).withVL(5)
              .build();
          ViolationContext violationContext = violationProcessor.processViolation(violation);
          if (violationContext.shouldCounterThreat()) {
            event.setCancelled(true);
            meta.cancelNextStop = true;
          }
          meta.restartFlagBreakTimestamp = meta.blockBreakTimestamp;
        }
        meta.breakProcess = true;
        meta.targetBlockPosition = blockPosition;
        break;
      }
      case STOP_DESTROY_BLOCK: {
        meta.blockBreakTimestamp = System.currentTimeMillis();
        meta.breakProcess = false;
        meta.targetBlockPosition = null;
        if (meta.cancelNextStop) {
          meta.cancelNextStop = false;
          event.setCancelled(true);
          // BlockPosition blockPosition = packet.getBlockPositionModifier().read(0);
          BlockPosition blockPosition = event.getPacket().getModifier()
              .withType(Lookup.serverClass("BlockPosition"), BlockPositionConverter.threadConverter())
              .read(0);
          refreshBlocksAround(player, blockPosition.toLocation(player.getWorld()));
        }
        break;
      }
      case ABORT_DESTROY_BLOCK:
        meta.breakProcess = false;
        meta.targetBlockPosition = null;
        break;
    }
  }

  static boolean isRepeatedActiveStart(
      boolean breakProcess,
      BlockPosition targetBlockPosition,
      BlockPosition blockPosition) {
    return breakProcess && targetBlockPosition != null && targetBlockPosition.equals(blockPosition);
  }

  static RestartAssessment assessRestartDelay(
      long restartDelay,
      double currentBalance,
      double balanceLimit) {
    double balance = clampBalance(currentBalance, balanceLimit);
    if (restartDelay >= CLOSE_ENOUGH_RESTART_DELAY_MILLIS) {
      balance *= 0.9D;
    } else {
      balance += EXPECTED_RESTART_DELAY_MILLIS - restartDelay;
    }
    return new RestartAssessment(balance > MAX_STORED_RESTART_ADVANTAGE_MILLIS, balance, restartDelay);
  }

  static boolean shouldReportRestart(
      RestartAssessment assessment,
      long blockBreakTimestamp,
      long restartFlagBreakTimestamp) {
    return assessment.shouldFlag() && restartFlagBreakTimestamp != blockBreakTimestamp;
  }

  private static double clampBalance(double balance, double balanceLimit) {
    double limit = Math.max(MAX_STORED_RESTART_ADVANTAGE_MILLIS, balanceLimit);
    return MathHelper.minmax(-limit, balance, limit);
  }

  static final class RestartAssessment {
    private final boolean shouldFlag;
    private final double balance;
    private final long delayMillis;

    private RestartAssessment(boolean shouldFlag, double balance, long delayMillis) {
      this.shouldFlag = shouldFlag;
      this.balance = balance;
      this.delayMillis = delayMillis;
    }

    boolean shouldFlag() {
      return shouldFlag;
    }

    double balance() {
      return balance;
    }

    long delayMillis() {
      return delayMillis;
    }
  }

  private void refreshBlocksAround(Player player, Location targetLocation) {
    Synchronizer.synchronize(() -> {
      player.updateInventory();
      refreshBlock(player, targetLocation);
      // for (EnumDirection direction : EnumDirection.values()) {
      // Location placedBlock =
      // targetLocation.clone().add(direction.getDirectionVec().convertToBukkitVec());
      // refreshBlock(player, placedBlock);
      // }
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
    private BlockPosition targetBlockPosition;
    private long blockBreakTimestamp;
    private boolean breakProcess;
    private double blockBreakStartVL;
    private long restartFlagBreakTimestamp = Long.MIN_VALUE;
    private boolean cancelNextStop;
  }
}

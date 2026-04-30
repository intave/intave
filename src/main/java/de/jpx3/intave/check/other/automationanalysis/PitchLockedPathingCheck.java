package de.jpx3.intave.check.other.automationanalysis;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.other.AutomationAnalysis;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.user.MessageChannel;
import de.jpx3.intave.user.MessageChannelSubscriptions;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.FLYING;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.violation.Violation.ViolationFlags.DISPLAY_IN_ALL_VERBOSE_MODES;

public final class PitchLockedPathingCheck extends MetaCheckPart<AutomationAnalysis, PitchLockedPathingCheck.PitchLockedPathingMeta> {
  private static final long DEBUG_FAST_INTERVAL = 500L;
  private static final long DEBUG_SLOW_INTERVAL = 1000L;
  private static final double PITCH_LOCKED_PATHING_VL = 2.0d;

  public PitchLockedPathingCheck(AutomationAnalysis parentCheck) {
    super(parentCheck, PitchLockedPathingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    PitchLockedPathingMeta checkMeta = metaOf(user);

    if (movementData.lastTeleport < 7 || meta.violationLevel().isInActiveTeleportBundle || player.isInsideVehicle()) {
      debug(
        user,
        checkMeta,
        DebugSlot.GATE,
        DEBUG_SLOW_INTERVAL,
        "pathing skip teleport=" + movementData.lastTeleport
          + " bundle=" + meta.violationLevel().isInActiveTeleportBundle
          + " vehicle=" + player.isInsideVehicle()
      );
      checkMeta.pathing.clearMovement();
      return;
    }

    PitchLockedPathingPattern.MovementResult result = checkMeta.pathing.pushMovement(
      movementData.positionX,
      movementData.positionY,
      movementData.positionZ,
      movementData.rotationYaw,
      movementData.rotationPitch
    );

    if (result.suspicious()) {
      String description = "pitch locked pathing"
        + " ticks:" + result.ticks()
        + " distance:" + MathHelper.formatDouble(result.distance(), 2)
        + " yaw:" + MathHelper.formatDouble(result.yawChange(), 2)
        + " ticksPerBlock:" + MathHelper.formatDouble(result.ticksPerBlock(), 3);
      debug(user, checkMeta, DebugSlot.MOVEMENT, 0L, "pathing signal " + description);
      outputDirectFlag(user, checkMeta, "automation:pitch_lock", description);
    } else if (result.ticks() > 0) {
      debug(
        user,
        checkMeta,
        DebugSlot.MOVEMENT,
        DEBUG_FAST_INTERVAL,
        "pathing window ticks=" + result.ticks()
          + " distance=" + MathHelper.formatDouble(result.distance(), 2)
          + " yaw=" + MathHelper.formatDouble(result.yawChange(), 2)
          + " ticksPerBlock=" + MathHelper.formatDouble(result.ticksPerBlock(), 3)
      );
    }
  }

  private void outputDirectFlag(
    User user,
    PitchLockedPathingMeta meta,
    String key,
    String description
  ) {
    String details = description;
    Violation violation = Violation.builderFor(AutomationAnalysis.class)
      .forPlayer(user.player())
      .withMessage("uses automation")
      .withDetails(details)
      .withDefaultThreshold()
      .appendFlags(DISPLAY_IN_ALL_VERBOSE_MODES)
      .withVL(PITCH_LOCKED_PATHING_VL)
      .withPlaceholder("identifier", key)
      .addGranular("signal", key)
      .build();
    ViolationContext context = Modules.violationProcessor().processViolation(violation);
    debug(
      user,
      meta,
      DebugSlot.VIOLATION,
      0L,
      "flag " + key
        + " vl+=" + MathHelper.formatDouble(PITCH_LOCKED_PATHING_VL, 2)
        + " checkVl=" + MathHelper.formatDouble(context.violationLevelAfter(), 2)
        + " details=" + description
    );
  }

  private void debug(User target, PitchLockedPathingMeta meta, DebugSlot slot, long interval, String message) {
    long now = System.currentTimeMillis();
    if (interval > 0L && !meta.debugAllowed(slot, now, interval)) {
      return;
    }
    for (Player receiver : MessageChannelSubscriptions.receiverOf(MessageChannel.DEBUG_AUTOMATION)) {
      if (receiver == null || !receiver.isOnline()) {
        continue;
      }
      User receiverUser = userOf(receiver);
      if (!receiverUser.receives(MessageChannel.DEBUG_AUTOMATION)) {
        continue;
      }
      if (
        receiverUser.hasChannelConstraint(MessageChannel.DEBUG_AUTOMATION)
          && !receiverUser.channelPlayerConstraint(MessageChannel.DEBUG_AUTOMATION).test(target.player())
      ) {
        continue;
      }
      receiver.sendMessage(
        IntavePlugin.prefix()
          + ChatColor.GRAY + "Automation "
          + ChatColor.RED + target.player().getName()
          + ChatColor.GRAY + " " + message
      );
    }
  }

  private enum DebugSlot {
    GATE,
    MOVEMENT,
    VIOLATION
  }

  public static class PitchLockedPathingMeta extends CheckCustomMetadata {
    private final PitchLockedPathingPattern pathing = new PitchLockedPathingPattern();
    private final long[] lastDebug = new long[DebugSlot.values().length];

    private boolean debugAllowed(DebugSlot slot, long now, long interval) {
      int index = slot.ordinal();
      if (now - lastDebug[index] < interval) {
        return false;
      }
      lastDebug[index] = now;
      return true;
    }
  }
}

/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.combat;

import com.comphenix.protocol.PacketType;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PlayerMoveReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.event.Cancellable;

import java.util.ArrayList;
import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class ClickSpeedLimiter extends MetaCheck<ClickSpeedLimiter.ClickSpeedLimiterMeta> {
	private final int maxCPS;

  public ClickSpeedLimiter(IntavePlugin plugin) {
    super("ClickSpeedLimiter", "clickspeedlimiter", ClickSpeedLimiterMeta.class);
	  this.maxCPS = configuration().settings().intInBoundsBy("max-cps", 8, 40, 20);
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void attackEntity(
    User user, EntityUseReader reader, Cancellable cancellable
  ) {
    ClickSpeedLimiterMeta meta = metaOf(user);
    if (reader.isAttackPacket()) {
      if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
        meta.attackCountArray[meta.attackArrayIndex]++;
      } else {
        meta.attacksDuringFlyingPackets.add(System.currentTimeMillis());
      }
    }
    double timeDiff = (System.currentTimeMillis() - meta.lastFlag) / 1000d;
    if (timeDiff < 1d) {
      cancellable.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK
    }
  )
  public void clientMovementUpdate(User user, PacketType packetType, PlayerMoveReader reader) {
    clientTickUpdate(user, packetType, reader);
  }

  @PacketSubscription(priority = ListenerPriority.HIGH, packetsIn = CLIENT_TICK_END)
  public void clientTickEnd(User user, PacketType packetType) {
    clientTickUpdate(user, packetType, null);
  }

  private void clientTickUpdate(User user, PacketType pt, @Nullable PlayerMoveReader reader) {
    // TODO: Check rod right click spam
    ClickSpeedLimiterMeta meta = metaOf(user);
    ProtocolMetadata protocol = user.meta().protocol();
    boolean sendsClientTickEnd = protocol.sendsClientTickEnd();
    if (sendsClientTickEnd && reader != null) {
      return;
    }

    AbilityMetadata abilities = user.meta().abilities();

    if (abilities.inGameModeIncludePending(AbilityTracker.GameMode.SPECTATOR)) {
      return;
    }

    boolean positionReminderPacket = user.protocolVersion() > ProtocolMetadata.VER_1_8
      && !sendsClientTickEnd
      && reader != null
      && isPositionReminderPacket(reader, meta, protocol);

    if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
      // 1.8
      meta.countAccuratePositionPackets = 20;
    } else if (sendsClientTickEnd) {
      meta.attackCountArray[meta.attackArrayIndex] = meta.attacksDuringFlyingPackets.size();
      meta.countAccuratePositionPackets++;
    } else if (meta.lastMovePacketType != null) {
      // 1.9+
      SimulationEnvironment movementData = user.meta().movement();

      if (positionReminderPacket
        || movementData.receivedFlyingPacketIn(0)
        || meta.lastMovePacketType.name().equals("FLYING") || meta.lastMovePacketType == PacketType.Play.Client.LOOK
      ) {
        meta.countAccuratePositionPackets = 0;
        long now = System.currentTimeMillis();
        int ticksToAdvance = positionReminderPacket
          ? 20
          : (int) ((now - meta.lastTickTimeStamp) / 50f);
        redistributeAttacks(meta, now, ticksToAdvance);
      } else {
        meta.attackCountArray[meta.attackArrayIndex] = meta.attacksDuringFlyingPackets.size();
        meta.countAccuratePositionPackets++;
      }
    }

    int sum = 0;
    for (int attacks : meta.attackCountArray) {
      sum += attacks;
    }
    if (sum > maxCPS) {
      int addedVL = 1;
      if (meta.countAccuratePositionPackets > 20) {
        // punishment can be 100% sure here
        addedVL = 3;
      }

      Violation violation = Violation.builderFor(ClickSpeedLimiter.class)
        .forUser(user)
        .withMessage("attacked too quickly")
        .withDetails(sum + " c/s")
        .withVL(addedVL)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        meta.lastFlag = System.currentTimeMillis();
      }
    }

    prepareNextTick(meta, pt);
  }

  private boolean isPositionReminderPacket(
    PlayerMoveReader reader, ClickSpeedLimiterMeta meta, ProtocolMetadata protocol
  ) {
    if (!reader.hasMovement()) {
      return false;
    }

    double positionX = reader.positionX();
    double positionY = reader.positionY();
    double positionZ = reader.positionZ();
    boolean reminder = meta.hasReportedPosition && isWithinPositionReminderThreshold(
      protocol.protocolVersion(),
      positionX - meta.lastReportedPositionX,
      positionY - meta.lastReportedPositionY,
      positionZ - meta.lastReportedPositionZ
    );
    meta.hasReportedPosition = true;
    meta.lastReportedPositionX = positionX;
    meta.lastReportedPositionY = positionY;
    meta.lastReportedPositionZ = positionZ;
    return reminder;
  }

  static boolean isWithinPositionReminderThreshold(
    int protocolVersion, double offsetX, double offsetY, double offsetZ
  ) {
    double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
    // This mirrors the client threshold; a regular update below it is the forced 20-tick reminder.
    double movementThresholdSquared = protocolVersion >= ProtocolMetadata.VER_1_18_2
      ? 0.0002 * 0.0002
      : 9.0E-4;
    return distanceSquared <= movementThresholdSquared;
  }

  static void redistributeAttacks(ClickSpeedLimiterMeta meta, long now, int ticksToAdvance) {
    int newIndex = meta.attackArrayIndex + ticksToAdvance;
    while (newIndex > 19)
      newIndex -= 20;
    while (newIndex < 0)
      newIndex += 20;
    meta.attackArrayIndex = newIndex;

    for (int i = 1; i <= ticksToAdvance; i++) {
      int index = meta.attackArrayIndex - i;

      while (index > 19)
        index -= 20;
      while (index < 0)
        index += 20;

      meta.attackCountArray[index] = 0;
    }

    for (long timeStampFromAttack : meta.attacksDuringFlyingPackets) {
      long timeDiff = now - timeStampFromAttack;
      int ticks = (int) (timeDiff / 50f);

      if (ticks < 20) {
        int index = meta.attackArrayIndex - ticks;

        while (index > 19)
          index -= 20;
        while (index < 0)
          index += 20;

        meta.attackCountArray[index]++;
      }
    }
  }

  private void prepareNextTick(ClickSpeedLimiterMeta meta, PacketType pt) {
    meta.attacksDuringFlyingPackets.clear();
    meta.lastMovePacketType = pt;

    meta.attackArrayIndex++;
    if (meta.attackArrayIndex > 19)
      meta.attackArrayIndex = 0;

    meta.attackCountArray[meta.attackArrayIndex] = 0;
    meta.lastTickTimeStamp = System.currentTimeMillis();
  }

  public static final class ClickSpeedLimiterMeta extends CheckCustomMetadata {
    private long lastFlag;
    PacketType lastMovePacketType;
    List<Long> attacksDuringFlyingPackets = new ArrayList<>();
    int[] attackCountArray = new int[20];
    int attackArrayIndex = 0;
    long lastTickTimeStamp = System.currentTimeMillis();
    int countAccuratePositionPackets;
    boolean hasReportedPosition;
    double lastReportedPositionX;
    double lastReportedPositionY;
    double lastReportedPositionZ;
  }
}

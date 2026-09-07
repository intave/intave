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
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheck;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.linker.packet.pe.PacketEventsIdMapper;
import de.jpx3.intave.module.tracker.player.AbilityTracker;
import de.jpx3.intave.module.violation.Violation;
import de.jpx3.intave.module.violation.ViolationContext;
import de.jpx3.intave.packet.PacketTypes;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.view.AttackView;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.packet.view.PacketEventsMovementView;
import de.jpx3.intave.packet.view.ProtocolLibAttackView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
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
    handleAttackEntity(user, new ProtocolLibAttackView(user.player(), reader, cancellable));
  }

  /**
   * PacketEvents entry point. Mirrors the ProtocolLib subscription above; the reader is not
   * released here because the ProtocolLib path never owned it either - the subscription linker
   * hands in a pooled reader and takes it back afterwards.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void attackEntity(PacketReceiveEvent event) {
    PacketEventsAttackView view = PacketEventsAttackView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleAttackEntity(userOf(player), view);
  }

  /** Engine independent entity interaction handling; see {@link AttackView}. */
  private void handleAttackEntity(User user, AttackView view) {
    ClickSpeedLimiterMeta meta = metaOf(user);
    if (view.isAttackPacket()) {
      if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
        meta.attackCountArray[meta.attackArrayIndex]++;
      } else {
        meta.attacksDuringFlyingPackets.add(System.currentTimeMillis());
      }
    }
    double timeDiff = (System.currentTimeMillis() - meta.lastFlag) / 1000d;
    if (timeDiff < 1d) {
      view.setCancelled(true);
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END
    }
  )
  public void clientTickUpdate(PacketEvent event) {
    PacketType pt = event.getPacketType();
    double[] reportedPosition = null;
    if (pt == PacketType.Play.Client.POSITION || pt == PacketType.Play.Client.POSITION_LOOK) {
      PacketContainer packet = event.getPacket();
      reportedPosition = new double[]{
        packet.getDoubles().read(0),
        packet.getDoubles().read(1),
        packet.getDoubles().read(2)
      };
    }
    handleClientTick(
      event.getPlayer(),
      PacketTypes.isClientEndTick(pt),
      // Kept as the very same test the stored packet type used to be put through.
      pt.name().equals("FLYING") || pt == PacketType.Play.Client.LOOK,
      reportedPosition
    );
  }

  /**
   * PacketEvents entry point. Mirrors the ProtocolLib subscription above; the three things the body
   * needs off the packet - whether it is the client tick end marker, whether it is a plain flying
   * or look packet, and the position a position packet reports - are resolved here and handed on,
   * so the shared body never sees an engine type.
   */
  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, CLIENT_TICK_END
    }
  )
  public void clientTickUpdate(Player player, PacketReceiveEvent event) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    PacketTypeCommon pt = event.getPacketType();
    double[] reportedPosition = null;
    if (pt == PeTypes.POSITION || pt == PeTypes.POSITION_LOOK) {
      PacketEventsMovementView view = PacketEventsMovementView.of(event);
      if (view != null) {
        reportedPosition = new double[]{view.positionX(), view.positionY(), view.positionZ()};
      }
    }
    handleClientTick(
      player,
      isClientEndTick(pt),
      pt == PeTypes.FLYING || pt == PeTypes.LOOK,
      reportedPosition
    );
  }

  /**
   * The four PacketEvents packet types this check tells apart, held in a nested class so they are
   * resolved the first time the PacketEvents path runs rather than when this check is loaded.
   * PacketEvents spells them differently from ProtocolLib, but they are the same four packets.
   */
  private static final class PeTypes {
    static final PacketTypeCommon FLYING =
      com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_FLYING;
    static final PacketTypeCommon LOOK =
      com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_ROTATION;
    static final PacketTypeCommon POSITION =
      com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_POSITION;
    static final PacketTypeCommon POSITION_LOOK =
      com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
  }

  /**
   * PacketEvents counterpart of {@link PacketTypes#isClientEndTick}.
   * <p>
   * Resolved through {@link PacketEventsIdMapper} rather than by referencing the constant, because
   * CLIENT_TICK_END does not exist on older PacketEvents releases; an unresolved packet yields an
   * empty list here, which simply never matches.
   */
  private static boolean isClientEndTick(PacketTypeCommon packetType) {
    return packetType != null && CLIENT_TICK_END_TYPES.contains(packetType);
  }

  private static final List<PacketTypeCommon> CLIENT_TICK_END_TYPES =
    PacketEventsIdMapper.typesOf(CLIENT_TICK_END);

  /**
   * Engine independent per client tick evaluation.
   * <p>
   * {@code reportedPosition} carries the x/y/z a position packet reported, and is null for every
   * other packet in the subscription - which is exactly the set the old
   * {@code isPositionReminderPacket} rejected on its packet type guard.
   */
  private void handleClientTick(
    Player player, boolean clientTickEnd, boolean flyingOrLookPacket, double[] reportedPosition
  ) {
    // TODO: Check rod right click spam
    User user = userOf(player);
    ClickSpeedLimiterMeta meta = metaOf(user);
    ProtocolMetadata protocol = user.meta().protocol();
    boolean sendsClientTickEnd = protocol.sendsClientTickEnd();
    if (sendsClientTickEnd && !clientTickEnd) {
      return;
    }

    AbilityMetadata abilities = user.meta().abilities();

    if (abilities.inGameModeIncludePending(AbilityTracker.GameMode.SPECTATOR)) {
      return;
    }

    boolean positionReminderPacket = user.protocolVersion() > ProtocolMetadata.VER_1_8
      && !sendsClientTickEnd
      && isPositionReminderPacket(reportedPosition, meta, protocol);

    if (user.protocolVersion() <= ProtocolMetadata.VER_1_8) {
      // 1.8
      meta.countAccuratePositionPackets = 20;
    } else if (sendsClientTickEnd) {
      meta.attackCountArray[meta.attackArrayIndex] = meta.attacksDuringFlyingPackets.size();
      meta.countAccuratePositionPackets++;
    } else if (meta.hasLastMovePacket) {
      // 1.9+
      SimulationEnvironment movementData = user.meta().movement();

      if (positionReminderPacket
        || movementData.receivedFlyingPacketIn(0)
        || meta.lastMoveWasFlyingOrLook
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
        .forPlayer(player)
        .withMessage("attacked too quickly")
        .withDetails(sum + " c/s")
        .withVL(addedVL)
        .build();
      ViolationContext violationContext = Modules.violationProcessor().processViolation(violation);
      if (violationContext.shouldCounterThreat()) {
        meta.lastFlag = System.currentTimeMillis();
      }
    }

//    player.sendMessage("" + sum);
    prepareNextTick(meta, flyingOrLookPacket);
  }

  private boolean isPositionReminderPacket(
    double[] reportedPosition, ClickSpeedLimiterMeta meta, ProtocolMetadata protocol
  ) {
    // Null for every packet in the subscription that is not a position packet, which is what the
    // packet type guard used to reject here - the side effects below stay skipped for those too.
    if (reportedPosition == null) {
      return false;
    }

    double positionX = reportedPosition[0];
    double positionY = reportedPosition[1];
    double positionZ = reportedPosition[2];
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

  private void prepareNextTick(ClickSpeedLimiterMeta meta, boolean flyingOrLookPacket) {
    meta.attacksDuringFlyingPackets.clear();
    meta.hasLastMovePacket = true;
    meta.lastMoveWasFlyingOrLook = flyingOrLookPacket;

    meta.attackArrayIndex++;
    if (meta.attackArrayIndex > 19)
      meta.attackArrayIndex = 0;

    meta.attackCountArray[meta.attackArrayIndex] = 0;
    meta.lastTickTimeStamp = System.currentTimeMillis();
  }

  public static final class ClickSpeedLimiterMeta extends CheckCustomMetadata {
    private long lastFlag;
    /** True once a packet from this subscription has been seen; replaces the stored packet type. */
    boolean hasLastMovePacket;
    /** Whether that packet was a plain flying or look packet. */
    boolean lastMoveWasFlyingOrLook;
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

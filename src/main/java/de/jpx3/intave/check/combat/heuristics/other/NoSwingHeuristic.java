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

package de.jpx3.intave.check.combat.heuristics.other;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class NoSwingHeuristic extends ClassicHeuristic<NoSwingHeuristic.NoSwingMeta> {

  public NoSwingHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.NO_SWING, NoSwingMeta.class);
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void entityHit(
    Player player, EntityUseReader reader
  ) {
    handleEntityHit(player, reader.isAttackPacket());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY
    }
  )
  public void entityHit(PacketReceiveEvent event) {
    PacketEventsAttackView view = PacketEventsAttackView.of(event);
    if (view == null) {
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleEntityHit(player, view.isAttackPacket());
  }

  /**
   * Engine independent handling; the only thing read off the packet is whether the interaction was
   * an attack, which both backends normalise the same way (see
   * {@link de.jpx3.intave.packet.view.AttackView}).
   */
  private void handleEntityHit(Player player, boolean attackPacket) {
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);
    if (attackPacket) {
      meta.attacksThisTick++;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void swing(PacketEvent event) {
    handleSwing(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      ARM_ANIMATION
    }
  )
  public void swing(Player player) {
    if (player == null) {
      return;
    }
    handleSwing(player);
  }

  /** Engine independent handling; the body only counts the packet, it never reads it. */
  private void handleSwing(Player player) {
    User user = userOf(player);
    NoSwingMeta meta = metaOf(user);

    meta.swingsThisTick++;
  }

  @PacketSubscription(
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    handleMovementPacket(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.NORMAL,
    packetsIn = {
      FLYING, LOOK, POSITION, POSITION_LOOK, VEHICLE_MOVE
    }
  )
  public void receiveMovementPacket(Player player) {
    if (player == null) {
      return;
    }
    handleMovementPacket(player);
  }

  /** Engine independent handling; the body only reads Intave's own metadata, not the packet. */
  private void handleMovementPacket(Player player) {
    User user = userOf(player);
    SimulationEnvironment movementData = user.meta().movement();
    NoSwingMeta meta = metaOf(user);

    if (movementData.ticksPast(TELEPORT) == 0) {
      return;
    }

    // todo: fix?
    if (user.meta().protocol().outdatedClient()) {
      return;
    }

    if (meta.attacksThisTick > 0) {
      if (meta.swingsThisTick == 0) {
        String checkName = "swing:miss";
        flag(user, "missing swing packet on attack", "");
        user.nerf(AttackNerfStrategy.CANCEL, checkName);
      }
    }

    prepareNextTick(meta);
  }

  private void prepareNextTick(NoSwingMeta meta) {
    meta.swingsThisTick = 0;
    meta.attacksThisTick = 0;
  }

  public static class NoSwingMeta extends CheckCustomMetadata {
    public int swingsThisTick;
    public int attacksThisTick;
  }
}

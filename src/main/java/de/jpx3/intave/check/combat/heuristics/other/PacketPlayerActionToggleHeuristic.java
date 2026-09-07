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
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.converter.PlayerActionResolver;
import de.jpx3.intave.packet.view.PacketEventsPlayerActionView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.*;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.FLYING_PACKET_ACCURATE;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PacketPlayerActionToggleHeuristic extends ClassicHeuristic<PacketPlayerActionToggleHeuristic.PacketSprintToggleHeuristicMeta> {
  public PacketPlayerActionToggleHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.SPRINT_TOGGLES, PacketSprintToggleHeuristicMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK
    }
  )
  public void receiveMovementPacket(PacketEvent event) {
    handleMovementPacket(event.getPlayer());
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      FLYING, POSITION, POSITION_LOOK, LOOK
    }
  )
  public void receiveMovementPacket(Player player) {
    if (player == null) {
      return;
    }
    handleMovementPacket(player);
  }

  /** Engine independent handling; the body only resets per tick counters, it never reads the packet. */
  private void handleMovementPacket(Player player) {
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(player);
    heuristicMeta.reset();
  }

  @PacketSubscription(
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityAction(PacketEvent event) {
    handleEntityAction(
      event.getPlayer(),
      PlayerActionResolver.resolveActionFromPacket(event.getPacket())
    );
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ENTITY_ACTION_IN
    }
  )
  public void receiveEntityAction(PacketReceiveEvent event) {
    PacketEventsPlayerActionView view = PacketEventsPlayerActionView.of(event);
    if (view == null) {
      // Not an entity action packet, or an action Intave has no equivalent for; neither of those
      // can be a sprint or sneak toggle, which is all this check looks at.
      return;
    }
    Player player = view.player();
    if (player == null) {
      return;
    }
    handleEntityAction(player, view.playerAction());
  }

  /**
   * Engine independent handling; see {@link de.jpx3.intave.packet.view.PlayerActionView}. The
   * action is the only thing read off the packet, and both backends normalise it the same way.
   */
  private void handleEntityAction(Player player, PlayerAction action) {
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    SimulationEnvironment movementData = meta.movement();
    AbilityMetadata abilityData = meta.abilities();
    ProtocolMetadata clientData = meta.protocol();
    PunishmentMetadata punishmentData = user.meta().punishment();
    PacketSprintToggleHeuristicMeta heuristicMeta = metaOf(user);

    boolean sprint = action == PlayerAction.START_SPRINTING || action == PlayerAction.STOP_SPRINTING;
    boolean sneak = action.isSneakRelated();
    if (!sprint && !sneak) {
      return;
    }

    if (abilityData.ignoringMovementPackets()) {
      heuristicMeta.reset();
      return;
    }

    if (movementData.ticksPast(TELEPORT) < 10) {
      return;
    }

    boolean flag = sprint
      ? heuristicMeta.sprintTogglesInTick++ >= 1
      : heuristicMeta.sneakTogglesInTick++ >= 1;

    if (flag) {
      boolean flyingPacketStream = clientData.emptyFlyingPacketsAreExplicitlySent();
      boolean checkable = flyingPacketStream || !movementData.receivedFlyingPacketIn(20);
      if (checkable) {
        String message = sprint
          ? "sent too many sprint toggles per tick"
          : "sent too many sneak toggles per tick";
        int toggleCount = sprint
          ? heuristicMeta.sprintTogglesInTick
          : heuristicMeta.sneakTogglesInTick;
        String details = "toggles: " + toggleCount;
        if (!flyingPacketStream) {
          details += ", last flying: " + movementData.ticksPast(FLYING_PACKET_ACCURATE) + " ticks ago";
        }
//        flag(user, message, details);
//
//        boolean cancel = (flyingPacketStream || Hypot.fast(movementData.offsetMotionX(), movementData.offsetMotionZ()) > 0.2) && heuristicMeta.threshold++ > 3;
//        if (cancel) {
//          if (sprint) {
//            //dmc12
//            user.nerf(AttackNerfStrategy.CANCEL, "sprint:toggles");
//          } else {
//            punishmentData.timeLastSneakToggleCancel = System.currentTimeMillis();
//            Synchronizer.synchronize(() -> DataWatcherAccess.setDataWatcherFlag(player, WATCHER_SNEAK_ID, false));
//          }
//        }
      }
    } else if (heuristicMeta.threshold > 0) {
      heuristicMeta.threshold -= 0.01;
    }
  }

  public static final class PacketSprintToggleHeuristicMeta extends CheckCustomMetadata {
    public int sprintTogglesInTick;
    public int sneakTogglesInTick;
    public double threshold;

    public void reset() {
      sprintTogglesInTick = 0;
      sneakTogglesInTick = 0;
    }
  }
}

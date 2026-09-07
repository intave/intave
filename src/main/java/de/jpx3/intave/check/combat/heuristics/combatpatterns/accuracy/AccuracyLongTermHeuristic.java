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

package de.jpx3.intave.check.combat.heuristics.combatpatterns.accuracy;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class AccuracyLongTermHeuristic extends ClassicHeuristic<AccuracyLongTermHeuristic.ClickAccuracyMeta> {
  public AccuracyLongTermHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_ACCURACY, ClickAccuracyMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY, ARM_ANIMATION
    }
  )
  public void evaluateFightAccuracy(PacketEvent event) {
    User user = userOf(event.getPlayer());
    if (event.getPacketType() == PacketType.Play.Client.ARM_ANIMATION) {
      handleFightAccuracy(user, true, false);
      return;
    }
    boolean isAttack;
    try (EntityUseReader reader = PacketReaders.readerOf(event.getPacket())) {
      isAttack = reader.isAttackPacket();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    handleFightAccuracy(user, false, isAttack);
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    packetsIn = {
      ATTACK_ENTITY, USE_ENTITY, ARM_ANIMATION
    }
  )
  public void evaluateFightAccuracy(Player player, PacketReceiveEvent event) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    // Of the subscribed packets only ATTACK_ENTITY and USE_ENTITY map to INTERACT_ENTITY, so a
    // view that refuses the packet identifies the arm animation.
    PacketEventsAttackView view = PacketEventsAttackView.of(event);
    handleFightAccuracy(userOf(player), view == null, view != null && view.isAttackPacket());
  }

  /** Engine independent swing versus attack accounting. */
  private void handleFightAccuracy(User user, boolean swing, boolean attack) {
    AttackMetadata attackData = user.meta().attack();
    ClickAccuracyMeta heuristicMeta = metaOf(user);
    Entity entity = attackData.lastAttackedEntity();
    if (entity == null || !entity.moving(0.05) || entity.ticksAlive < 200) {
      return;
    }
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) {
      return;
    }
    if (swing) {
      heuristicMeta.swings++;
    } else {
	    if (attack) {
        heuristicMeta.attacks++;
        heuristicMeta.swings--;
        double failRate = (heuristicMeta.swings / heuristicMeta.attacks) * 100.0;
//        Synchronizer.synchronize(() -> player.sendMessage(String.valueOf(failRate)));
        if (heuristicMeta.attacks > 80) {
          if (failRate >= 0 && failRate < 3) {
            flag(
              user,
              "maintains high attack accuracy",
              "fail rate: " + MathHelper.formatDouble(failRate, 2) + "%"
            );
          }
          heuristicMeta.attacks = 0;
          heuristicMeta.swings = 0;
        }
      }
    }
  }

  public static class ClickAccuracyMeta extends CheckCustomMetadata {
    public double attacks;
    public double swings;
  }
}

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
import com.google.common.collect.Lists;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.ClassicHeuristic;
import de.jpx3.intave.check.combat.heuristics.HeuristicsClassicType;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.view.PacketEventsAttackView;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class AccuracyHitboxCornerHeuristic extends ClassicHeuristic<AccuracyHitboxCornerHeuristic.PerfectAttackMeta> {
  public AccuracyHitboxCornerHeuristic(Heuristics parentCheck) {
    super(parentCheck, HeuristicsClassicType.ATTACK_ACCURACY, PerfectAttackMeta.class);
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
    PerfectAttackMeta heuristicMeta = metaOf(user);
    Entity attackedEntity = attackData.lastAttackedEntity();

    if (attackedEntity != null && !attackedEntity.moving(0.05)) {
      return;
    }
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) {
      return;
    }

    if (swing) {
      heuristicMeta.swings++;
    } else if (attack) {
      heuristicMeta.attacks++;
      heuristicMeta.swings--;
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    handleMovement(userOf(event.getPlayer()));
  }

  @PacketSubscription(
    engine = Engine.PACKETEVENTS,
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(Player player) {
    // PacketEvents can deliver a packet before the Bukkit player exists.
    if (player == null) {
      return;
    }
    handleMovement(userOf(player));
  }

  /** Engine independent rotation sampling; the packet itself is never read. */
  private void handleMovement(User user) {
    AttackMetadata attackData = user.meta().attack();
    MovementMetadata movementData = user.meta().movement();
    PerfectAttackMeta heuristicMeta = metaOf(user);

    if (!attackData.recentlyAttacked(1000) || attackData.recentlySwitchedEntity(500) || attackData.lastReach() < 1.0) {
      return;
    }

    float distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    float pitchSpeed = MathHelper.distanceInDegrees(movementData.rotationPitch, movementData.lastRotationPitch);

    if (heuristicMeta.distanceToPerfectYawList.size() > 20) {
      double distanceAverage = averageOf(heuristicMeta.distanceToPerfectYawList);
      double yawSpeedAverage = averageOf(heuristicMeta.yawSpeedList);
      double failRate = (heuristicMeta.swings / heuristicMeta.attacks) * 100.0;

      if (failRate < 5 && (yawSpeedAverage > 10 || distanceAverage > 10)) {
        heuristicMeta.vl++;
        String details = "fail: " + MathHelper.formatDouble(failRate, 2)
          + "%, rotation: " + MathHelper.formatDouble(yawSpeedAverage, 2)
          + ", distance: " + MathHelper.formatDouble(distanceAverage, 2)
          + ", vl: " + MathHelper.formatDouble(heuristicMeta.vl, 2);
        flag(user, "maintains high attack accuracy whilst aiming at hitbox corners", details);
        if (heuristicMeta.vl >= 2) {
          user.nerf(AttackNerfStrategy.DMG_MEDIUM, "13");
        }
      } else if (heuristicMeta.vl > 0) {
        heuristicMeta.vl -= 0.2;
      }

      heuristicMeta.attacks = 0;
      heuristicMeta.swings = 0;
      heuristicMeta.distanceToPerfectYawList.clear();
      heuristicMeta.yawSpeedList.clear();
    }

    if (yawSpeed > 5 && attackData.recentlyAttacked(60)) {
      heuristicMeta.distanceToPerfectYawList.add(distanceToPerfectYaw);
      heuristicMeta.yawSpeedList.add(yawSpeed + pitchSpeed);
    }
  }

  private double averageOf(List<? extends Number> data) {
    double sum = 0;
    for (Number element : data) {
      sum += element.doubleValue();
    }
    if (sum == 0) {
      return 0;
    }
    return sum / data.size();
  }

  public static final class PerfectAttackMeta extends CheckCustomMetadata {
    public double vl;
    public double attacks;
    public double swings;
    public final List<Float> distanceToPerfectYawList = Lists.newArrayList();
    public final List<Float> yawSpeedList = Lists.newArrayList();
  }
}

package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.google.common.collect.Lists;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.*;

public final class PerfectAttackHeuristic extends MetaCheckPart<Heuristics, PerfectAttackHeuristic.PerfectAttackMeta> {
  public PerfectAttackHeuristic(Heuristics parentCheck) {
    super(parentCheck, PerfectAttackMeta.class);
  }

  @PacketSubscription(
    packetsIn = {
      USE_ENTITY, ARM_ANIMATION
    }
  )
  public void evaluateFightAccuracy(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    AttackMetadata attackData = user.meta().attack();
    PerfectAttackMeta heuristicMeta = metaOf(user);
    PacketType packetType = event.getPacketType();
    PacketContainer packet = event.getPacket();
    Entity attackedEntity = attackData.lastAttackedEntity();

    if (attackedEntity != null && !attackedEntity.moving(0.05)) {
      return;
    }
    if (!attackData.recentlyAttacked(500) || attackData.recentlySwitchedEntity(1000)) {
      return;
    }

    if (packetType == PacketType.Play.Client.ARM_ANIMATION) {
      heuristicMeta.swings++;
    } else {
      EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
      if (action == null) {
        action = packet.getEnumEntityUseActions().read(0).getAction();
      }
      if (action == EnumWrappers.EntityUseAction.ATTACK) {
        heuristicMeta.attacks++;
        heuristicMeta.swings--;
      }
    }
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(ProtocolPacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
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
        String description = "maintains high attack accuracy whilst aiming at hitbox corners " +
          "(fail:" + MathHelper.formatDouble(failRate, 2)
          + "%, r:" + MathHelper.formatDouble(yawSpeedAverage, 2)
          + ", d:" + MathHelper.formatDouble(distanceAverage, 2)
          + ") vl:" + MathHelper.formatDouble(heuristicMeta.vl, 2);
        int options = LIMIT_4 | SUGGEST_MINING | DELAY_16s;
        Anomaly anomaly = Anomaly.anomalyOf("51", Confidence.MAYBE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        if (heuristicMeta.vl >= 2) {
          //dmc13
//          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "13");
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
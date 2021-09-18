package de.jpx3.intave.check.combat.heuristics.detect;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.WrappedEntity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.*;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;

public final class RotationAccuracyYawHeuristic extends MetaCheckPart<Heuristics, RotationAccuracyYawHeuristic.RotationAccuracyHeuristicMeta> {
  private final IntavePlugin plugin;

  public RotationAccuracyYawHeuristic(Heuristics parentCheck) {
    super(parentCheck, RotationAccuracyHeuristicMeta.class);
    this.plugin = IntavePlugin.singletonInstance();
  }

  @PacketSubscription(
    priority = ListenerPriority.HIGH,
    packetsIn = {
      POSITION_LOOK, LOOK
    }
  )
  public void receiveMovement(PacketEvent event) {
    Player player = event.getPlayer();
    User user = userOf(player);
    MetadataBundle meta = user.meta();
    MovementMetadata movementData = meta.movement();
    AttackMetadata attackData = meta.attack();
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(player);
    WrappedEntity entity = attackData.lastAttackedEntity();
    float rotationYaw = movementData.rotationYaw;
    float perfectYaw = attackData.perfectYaw();
    float yawSpeed = MathHelper.distanceInDegrees(rotationYaw, movementData.lastRotationYaw);
    float distanceToPerfectYaw = MathHelper.distanceInDegrees(perfectYaw, rotationYaw);
    if (entity == null || movementData.lastTeleport < 5) {
      return;
    }
    if (attackData.recentlyAttacked(150)
      && yawSpeed > 1001
      && attackData.lastReach() > 1.0
      && !attackData.recentlySwitchedEntity(200)
    ) {
      if (heuristicMeta.snapVL++ > 0) {
        String description = "suspicious rotation snap (" + yawSpeed + ")";
        int options = LIMIT_4 | SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf("86", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        //dmc16
        user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "16");
      }
    } else if (heuristicMeta.snapVL > 0) {
      heuristicMeta.snapVL -= 0.1;
    }
    if (entity.moving(0.05) && attackData.recentlyAttacked(1000)) {
      if (yawSpeed > 1.0) {
        if (yawSpeed > 3.0) {
          double increase = MathHelper.minmax(-2.5, (2.2 - distanceToPerfectYaw) * Math.min(6, yawSpeed), 2);
          heuristicMeta.followBalance += increase;
          if (heuristicMeta.followBalance < 0) {
            heuristicMeta.followBalance = 0;
          }
          if (heuristicMeta.followBalance > 25) {
            String description = "follows entity movement too precisely";
            int options = LIMIT_4 | SUGGEST_MINING | DELAY_64s;
            Anomaly anomaly = Anomaly.anomalyOf("81", Confidence.MAYBE, Anomaly.Type.KILLAURA, description, options);
            parentCheck().saveAnomaly(player, anomaly);
            heuristicMeta.followBalance -= 7;
//            plugin.eventService().attackCancelService().requestDamageCancel(user, AttackCancelType.LIGHT);
          }
        }
        // Check perfect yaw
        if (distanceToPerfectYaw == 0) {
          String description = "rotated yaw too precisely (0.0)";
          int options = LIMIT_2 | DELAY_128s | SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("82", Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          //dmc17
          user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "17");
        }
        // Check yaw accuracy
        if (yawSpeed > 3.0) {
          double expectedDifference = 2.0;//Math.min(10, yawSpeed * 0.6);
          heuristicMeta.balanceYawAccuracy += expectedDifference - (distanceToPerfectYaw / 0.8);
          heuristicMeta.balanceYawAccuracy = Math.max(0, heuristicMeta.balanceYawAccuracy);
          int suspiciousLevel = (int) heuristicMeta.balanceYawAccuracy;
          if (suspiciousLevel > 8) {
            if (heuristicMeta.rotationAccuracyVL++ > 3) {
              String description = "high accuracy rotation yaw vl:" + suspiciousLevel;
              int options = LIMIT_2 | DELAY_32s | SUGGEST_MINING;
              Anomaly anomaly = Anomaly.anomalyOf("83", Confidence.LIKELY, Anomaly.Type.KILLAURA, description, options);
              parentCheck().saveAnomaly(player, anomaly);
              //dmc18
              user.applyAttackNerfer(AttackNerfStrategy.HT_MEDIUM, "18");
            }
          } else if (heuristicMeta.rotationAccuracyVL > 0) {
            heuristicMeta.rotationAccuracyVL -= 0.005;
          }
        }
        // Check yaw accuracy (other)
        if (distanceToPerfectYaw > 4.0) {
          heuristicMeta.balanceYawAccuracyOther = 0;
        } else if (heuristicMeta.balanceYawAccuracyOther++ > 50) {
          String description = "keeps high yaw accuracy in " + heuristicMeta.balanceYawAccuracyOther + " rotations";
          int options = LIMIT_2 | DELAY_32s | SUGGEST_MINING;
          Anomaly anomaly = Anomaly.anomalyOf("84", Confidence.MAYBE, Anomaly.Type.KILLAURA, description, options);
          parentCheck().saveAnomaly(player, anomaly);
          heuristicMeta.balanceYawAccuracyOther = 0;
          //dmc19
          user.applyAttackNerfer(AttackNerfStrategy.HT_LIGHT, "19");
        }
      }
    }
    if (
      Hypot.fast(movementData.motionX(), movementData.motionZ()) < 0.05
      || attackData.lastReach() < 1
      || !entity.moving(0.05)
    ) {
      return;
    }
    int direction = perfectYaw > rotationYaw ? 1 : 0;
    boolean sameYawDirection = heuristicMeta.lastBodyDirection == direction;
    if (!sameYawDirection) {
      heuristicMeta.bitBoxCornerBalance = 0;
    } else if (yawSpeed > 3) {
      float deviation = MathHelper.distanceInDegrees(heuristicMeta.prevDistanceToPerfectYaw, distanceToPerfectYaw);
      double increase = MathHelper.minmax(-0.2, (1 - deviation) * 4, 4);
      heuristicMeta.bitBoxCornerBalance = (int) MathHelper.minmax(0, heuristicMeta.bitBoxCornerBalance + increase, 100);
      if (heuristicMeta.bitBoxCornerBalance > 30) {
        long lastDetection = System.currentTimeMillis() - heuristicMeta.lastHARYAnomaly;
        int options = SUGGEST_MINING | DELAY_16s | LIMIT_2;
        Confidence confidence = lastDetection < 2000 ? Confidence.LIKELY : Confidence.PROBABLE;
        Anomaly anomaly = Anomaly.anomalyOf("85", confidence, Anomaly.Type.KILLAURA, "high accuracy rotation yaw on hit-box corners", options);
        parentCheck().saveAnomaly(player, anomaly);
        heuristicMeta.bitBoxCornerBalance -= 20;
        heuristicMeta.lastHARYAnomaly = System.currentTimeMillis();
      }
    }
    heuristicMeta.lastBodyDirection = direction;
    heuristicMeta.prevDistanceToPerfectYaw = distanceToPerfectYaw;
  }

  public final static class RotationAccuracyHeuristicMeta extends CheckCustomMetadata {
    private double balanceYawAccuracy;
    private double balanceYawAccuracyOther;
    private double rotationAccuracyVL;
    private double followBalance;
    private double snapVL;

    private long lastHARYAnomaly;

    private int lastBodyDirection;
    private int bitBoxCornerBalance;
    private float prevDistanceToPerfectYaw;
  }
}
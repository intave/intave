package de.jpx3.intave.check.combat.heuristics.detect.combatpatterns;

import com.comphenix.protocol.events.PacketEvent;
import com.google.common.collect.Lists;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.check.MetaCheckPart;
import de.jpx3.intave.check.combat.Heuristics;
import de.jpx3.intave.check.combat.heuristics.Anomaly;
import de.jpx3.intave.check.combat.heuristics.Confidence;
import de.jpx3.intave.connect.sibyl.SibylCensor;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.linker.packet.ListenerPriority;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.module.mitigate.AttackNerfStrategy;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AttackMetadata;
import de.jpx3.intave.user.meta.CheckCustomMetadata;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;

import java.util.List;

import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_128s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_16s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_32s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.DELAY_64s;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_1;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_2;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.LIMIT_4;
import static de.jpx3.intave.check.combat.heuristics.Anomaly.AnomalyOption.SUGGEST_MINING;
import static de.jpx3.intave.math.MathHelper.averageOf;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.LOOK;
import static de.jpx3.intave.module.linker.packet.PacketId.Client.POSITION_LOOK;
import static de.jpx3.intave.module.mitigate.AttackNerfStrategy.DMG_LIGHT;

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
    Entity entity = attackData.lastAttackedEntity();
    float rotationYaw = movementData.rotationYaw;
    float perfectYaw = attackData.perfectYaw();
    float closestPerfectYaw = attackData.perfectClosestYaw();
    float yawSpeed = MathHelper.distanceInDegrees(rotationYaw, movementData.lastRotationYaw);
    float distanceToPerfectYaw = MathHelper.distanceInDegrees(perfectYaw, rotationYaw);
    float distanceToClosestPerfectYaw = MathHelper.distanceInDegrees(closestPerfectYaw, rotationYaw);
    if (entity == null || movementData.lastTeleport < 5 || !attackData.recentlyAttacked(1000)) {
      return;
    }

    checkSnap(user, yawSpeed);

    if (entity.moving(0.05) && yawSpeed > 1.0) {
      checkFollow(user, yawSpeed, distanceToPerfectYaw);
      checkPerfectYaw(user, distanceToClosestPerfectYaw, distanceToPerfectYaw);
      checkShortTermAccuracy(user, yawSpeed, distanceToPerfectYaw);
      checkLongTermAccuracy(user, distanceToPerfectYaw);
    }

    checkHitboxCorners(user, distanceToPerfectYaw, perfectYaw, yawSpeed, entity);
    checkYawAccuracyAvg(user, entity);
  }

  private void checkSnap(User user, float yawSpeed) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();

    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(player);

    if (attackData.recentlyAttacked(150)
      && yawSpeed > 1001
      && attackData.lastReach() > 1.0
      && !attackData.recentlySwitchedEntity(200)
    ) {
      if (heuristicMeta.snapVL++ > 0) {
        String description = "suspicious rotation snap (" + yawSpeed + ")";
        int options = LIMIT_4 | SUGGEST_MINING;
        String checkName = resolveCheckName(0);
        Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(player, anomaly);
        user.nerf(AttackNerfStrategy.CRITICALS, checkName);
      }
    } else if (heuristicMeta.snapVL > 0) {
      heuristicMeta.snapVL -= 0.1;
    }
  }

  private void checkFollow(User user, float yawSpeed, float distanceToPerfectYaw) {
    if (yawSpeed < 3.0) {
      return;
    }

    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);
    double increase = MathHelper.minmax(-2.5, (2.2 - distanceToPerfectYaw) * Math.min(6, yawSpeed), 2);
    heuristicMeta.followBalance += increase;
    if (heuristicMeta.followBalance < 0) {
      heuristicMeta.followBalance = 0;
    }
    if (heuristicMeta.followBalance > 25) {
      String description = "follows entity movement too precisely";
      int options = LIMIT_2 | LIMIT_1 | SUGGEST_MINING | DELAY_64s;
      String checkName = resolveCheckName(1);
      Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
      parentCheck().saveAnomaly(user.player(), anomaly);
      heuristicMeta.followBalance -= 7;
      user.nerf(AttackNerfStrategy.CRITICALS, checkName);
    }
  }

  private void checkPerfectYaw(User user, float distanceToClosestPerfectYaw, float distanceToPerfectYaw) {
    if (distanceToPerfectYaw == 0 || distanceToClosestPerfectYaw == 0) {
      String description = "rotated yaw too precise (0.0)";
      int options = LIMIT_2 | DELAY_128s | SUGGEST_MINING;
      String checkName = resolveCheckName(2);
      Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
      parentCheck().saveAnomaly(user.player(), anomaly);
      user.nerf(AttackNerfStrategy.CRITICALS, checkName);
    }
  }

  private void checkShortTermAccuracy(User user, float yawSpeed, float distanceToPerfectYaw) {
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);

    if (yawSpeed < 3.0) {
      return;
    }

    double expectedDifference = 2.0;//Math.min(10, yawSpeed * 0.6);
    heuristicMeta.balanceYawAccuracy += expectedDifference - (distanceToPerfectYaw / 0.8);
    heuristicMeta.balanceYawAccuracy = Math.max(0, heuristicMeta.balanceYawAccuracy);
    int suspiciousLevel = (int) heuristicMeta.balanceYawAccuracy;
    if (suspiciousLevel > 8) {
      if (heuristicMeta.rotationAccuracyVL++ > 3) {
        String description = "high accuracy rotation yaw vl:" + suspiciousLevel;
        int options = LIMIT_2 | DELAY_32s | SUGGEST_MINING;
        String checkName = resolveCheckName(3);
        Anomaly anomaly = Anomaly.anomalyOf(checkName, Confidence.PROBABLE, Anomaly.Type.KILLAURA, description, options);
        parentCheck().saveAnomaly(user.player(), anomaly);
        user.nerf(AttackNerfStrategy.CRITICALS, checkName);
      }
    } else if (heuristicMeta.rotationAccuracyVL > 0) {
      heuristicMeta.rotationAccuracyVL -= 0.005;
    }
  }

  private void checkLongTermAccuracy(User user, float distanceToPerfectYaw) {
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);

    if (distanceToPerfectYaw > 4.0) {
      heuristicMeta.balanceYawAccuracyOther = 0;
    } else if (heuristicMeta.balanceYawAccuracyOther++ > 50) {
      String description = "keeps high yaw accuracy in " + (int) heuristicMeta.balanceYawAccuracyOther + " rotations";
      int options = LIMIT_2 | DELAY_32s | SUGGEST_MINING;
      Anomaly anomaly = Anomaly.anomalyOf(resolveCheckName(4), Confidence.MAYBE, Anomaly.Type.KILLAURA, description, options);
      parentCheck().saveAnomaly(user.player(), anomaly);
      heuristicMeta.balanceYawAccuracyOther = 0;
    }
  }

  private void checkHitboxCorners(User user, float distanceToPerfectYaw, float perfectYaw, float yawSpeed, Entity target) {
    MetadataBundle meta = user.meta();

    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();

    float rotationYaw = movementData.rotationYaw;
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);

    if (Hypot.fast(movementData.motionX(), movementData.motionZ()) < 0.05
      || attackData.lastReach() < 1
      || !target.moving(0.05)) {
      return;
    }
    int direction = perfectYaw > rotationYaw ? 1 : 0;
    boolean sameYawDirection = heuristicMeta.lastBodyDirection == direction;
    if (!sameYawDirection) {
      heuristicMeta.bitBoxCornerBalance = 0;
    } else if (yawSpeed > 3 && !movementData.isInRidingVehicle()) {
      float deviation = MathHelper.distanceInDegrees(heuristicMeta.prevDistanceToPerfectYaw, distanceToPerfectYaw);
      double increase = MathHelper.minmax(-0.2, (1 - deviation) * 4, 4);
      heuristicMeta.bitBoxCornerBalance = (int) MathHelper.minmax(0, heuristicMeta.bitBoxCornerBalance + increase, 100);
      if (heuristicMeta.bitBoxCornerBalance > 30) {
        int options = SUGGEST_MINING | DELAY_16s | LIMIT_2;
        Confidence confidence = Confidence.LIKELY;
        String checkName = resolveCheckName(5);
        Anomaly anomaly = Anomaly.anomalyOf(checkName, confidence, Anomaly.Type.KILLAURA, "high accuracy rotation yaw on hit-box corners", options);
        parentCheck().saveAnomaly(user.player(), anomaly);
        heuristicMeta.bitBoxCornerBalance -= 20;
        user.nerf(DMG_LIGHT, checkName);
      }
    }
    heuristicMeta.lastBodyDirection = direction;
    heuristicMeta.prevDistanceToPerfectYaw = distanceToPerfectYaw;
  }

  private void checkYawAccuracyAvg(User user, Entity target) {
    MetadataBundle meta = user.meta();
    AttackMetadata attackData = meta.attack();
    MovementMetadata movementData = meta.movement();
    RotationAccuracyHeuristicMeta heuristicMeta = metaOf(user);

    double distanceToPerfectYaw = MathHelper.distanceInDegrees(attackData.perfectYaw(), movementData.rotationYaw);
    float yawSpeed = MathHelper.distanceInDegrees(movementData.rotationYaw, movementData.lastRotationYaw);
    if (heuristicMeta.yawSpeeds.size() > 40) {
      double yawAverage = averageOf(heuristicMeta.yawSpeeds);
      double maxDistanceToPerfectYaw = heuristicMeta.distancesToPerfectYaw
        .stream()
        .mapToDouble(Double::doubleValue)
        .max()
        .orElse(0);
      List<Double> angleData = heuristicMeta.distancesToPerfectYaw;
      double averageRatio = yawAverage / averageOf(angleData);
      double maxRatio = maxDistanceToPerfectYaw / yawAverage;
      if (maxRatio < 2 && maxDistanceToPerfectYaw < 30) {
//        String descriptor = "rotated suspiciously (" + MathHelper.formatDouble(maxRatio, 4) + " / " + MathHelper.formatDouble(maxDistanceToPerfectYaw, 4) + ")";
        String descriptor = SibylCensor.thisPlease("rotated suspiciously (%s / %s)", MathHelper.formatDouble(maxRatio, 4), MathHelper.formatDouble(maxDistanceToPerfectYaw, 4));
        int options = Anomaly.AnomalyOption.LIMIT_8 | Anomaly.AnomalyOption.SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf("yaw:acc(", Confidence.MAYBE, Anomaly.Type.KILLAURA, descriptor, options);
        parentCheck().saveAnomaly(user.player(), anomaly);
      }
      if (yawAverage >= 3.5 && maxDistanceToPerfectYaw <= 12.5 && averageRatio > 1) {
        String descriptor = "precise rotation yaw (" + MathHelper.formatDouble(yawAverage, 4) + ")";
        int options = Anomaly.AnomalyOption.LIMIT_4 | Anomaly.AnomalyOption.SUGGEST_MINING;
        Anomaly anomaly = Anomaly.anomalyOf(resolveCheckName(6), Confidence.MAYBE, Anomaly.Type.KILLAURA, descriptor, options);
        parentCheck().saveAnomaly(user.player(), anomaly);
      }
      heuristicMeta.distancesToPerfectYaw.clear();
      heuristicMeta.yawSpeeds.clear();
    }
    if (target.moving(0.05)) {
      heuristicMeta.distancesToPerfectYaw.add(distanceToPerfectYaw);
      heuristicMeta.yawSpeeds.add((double) yawSpeed);
    }
  }

  private String resolveCheckName(int id) {
    return "yaw:acc(" + id + ")";
  }

  public static final class RotationAccuracyHeuristicMeta extends CheckCustomMetadata {
    private double balanceYawAccuracy;
    private double balanceYawAccuracyOther;
    private double rotationAccuracyVL;
    private double followBalance;
    private double snapVL;

    private int lastBodyDirection;
    private int bitBoxCornerBalance;
    private float prevDistanceToPerfectYaw;

    private final List<Double> yawSpeeds = Lists.newArrayList();
    private final List<Double> distancesToPerfectYaw = Lists.newArrayList();
  }
}
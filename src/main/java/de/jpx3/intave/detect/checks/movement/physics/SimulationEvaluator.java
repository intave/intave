package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import de.jpx3.intave.world.collision.Collision;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class SimulationEvaluator {
  private final static double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  public double calculateVerticalViolationLevelIncrease(
    User user,
    double predictedY,
    boolean onLadder,
    boolean collidedWithBoat
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata clientData = meta.protocol();
    MovementMetadata movementData = meta.movement();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    Pose pose = movementData.pose();
    double receivedMotionX = movementData.motionX();
    double receivedMotionY = movementData.motionY();
    double receivedMotionZ = movementData.motionZ();
    double differenceY = Math.abs(receivedMotionY - predictedY);
    boolean accountedSkippedMovement = movementData.recentlyEncounteredFlyingPacket(2);
    double legitimateDeviation = accountedSkippedMovement ? 1e-2 : 1e-5;
    // MotionY calculations with sin/cos (FastMath affected)
    boolean fastMathAffected = pose == Pose.SWIMMING || pose == Pose.FALL_FLYING;
    if (fastMathAffected) {
      legitimateDeviation = 0.001;
    }

    if ((movementData.pastPushedByWaterFlow < 10 || movementData.inLava()) && distanceMoved < 0.2) {
      legitimateDeviation = 0.02;
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    // Firework
    if (movementData.fireworkTolerant) {
      legitimateDeviation = Math.max(legitimateDeviation, 0.8);
    }

    //TODO: Bad fix
    if (clientData.applyModernCollider() && Math.abs(differenceY - 0.2) < 1e-5 && movementData.lastOnGround && !movementData.onGround) {
      if (!Collision.isNotInsideBlocks(player, movementData.boundingBox().addCoord(movementData.motionX(), 0.201, movementData.motionZ()))) {
        differenceY = 0;
      }
    }

    if (movementData.recentlyEncounteredFlyingPacket(3) && differenceY > 1e-3) {
      boolean inLiquid = movementData.pastWaterMovement <= 10 || movementData.inLava();
      int allowedPackets = Hypot.fast(movementData.motionX(), movementData.motionZ()) < 0.03 ? 3 : 1;
      if (inLiquid || movementData.physicsPacketRelinkFlyVL++ <= allowedPackets) {
        legitimateDeviation = Math.max(legitimateDeviation, inLiquid ? 0.1 : 0.03);
      }
    }

    if (movementData.physicsUnpredictableVelocityExpected) {
      double velocityY = movementData.lastVelocity.getY();
      legitimateDeviation = Math.max(legitimateDeviation, velocityY * 1.2 - differenceY);
    }

    if (collidedWithBoat && movementData.motionY() < 0.605) {
      if (movementData.enforceBoatStep) {
        if (movementData.motionY() < 0.1) {
          legitimateDeviation = Math.max(legitimateDeviation, 10);
        }
        movementData.enforceBoatStep = false;
      } else if (movementData.physicsMotionY < 0) {
        legitimateDeviation = Math.max(legitimateDeviation, 10);
        if (movementData.motionY() > movementData.jumpMotion()) {
          movementData.enforceBoatStep = true;
        }
      }
    }

    boolean criticalWeb = receivedMotionY > -0.01
      && movementData.inWeb
      && movementData.positionY % 1 > 0.1
      && movementData.pastExternalVelocity != 0;

    if (movementData.inWeb) {
      legitimateDeviation = criticalWeb ? 1e-6 : 0.13;
    }

    if (movementData.pastInWeb < 10 && !movementData.inWeb && differenceY < 0.1) {
      legitimateDeviation = 0.1;
    }

    if (movementData.recentlyEncounteredFlyingPacket(1) && movementData.pastExternalVelocity <= 4) {
      legitimateDeviation = 0.03;
    }

    // Jump out of water
    if (movementData.pastWaterMovement <= 3) {
      double liquidPositionY;
      if (clientData.waterUpdate()) {
        liquidPositionY = receivedMotionY + 0.6f - movementData.positionY + movementData.verifiedPositionY;
      } else {
        liquidPositionY = receivedMotionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementHelper.isOffsetPositionInLiquid(
        player, movementData.boundingBox(), receivedMotionX, liquidPositionY, receivedMotionZ
      );
      boolean maybeCollidedHorizontally = Collision.nearBySolidBlock(player.getWorld(), movementData.boundingBox().grow(0.2));
      if (maybeCollidedHorizontally && offsetPositionInLiquid && receivedMotionY < 0.4) {
        legitimateDeviation = Math.max(legitimateDeviation, 0.7f);
      }
    }

    double abuseVertically = Math.max(0, differenceY - legitimateDeviation);
    boolean allowDeviation = fastMathAffected || movementData.inLava();
    double multiplier;

    if (abuseVertically > 0.1 && !allowDeviation) {
      multiplier = 5000;
    } else if (abuseVertically > 0.009 && !allowDeviation) {
      abuseVertically = Math.max(abuseVertically, 0.1);
      multiplier = 200;
    } else {
      multiplier = 100;
    }

    if (pose == Pose.FALL_FLYING) {
      if (movementData.motionY() >= 0 && movementData.onGround) {
        multiplier *= 0.1;
      } else {
        multiplier *= 0.25;
      }
    }

    if (criticalWeb) {
      multiplier *= 40;
    }

    if (onLadder && movementData.motionY() <= LADDER_UPWARDS_MOTION) {
      abuseVertically = 0;
    }

    // Long teleport
    if (movementData.pastLongTeleport <= 10 && movementData.motionY() < -0.097 && movementData.motionY() > -0.099) {
      double horizontalDistance = Hypot.fast(receivedMotionX, receivedMotionZ);
      if (horizontalDistance < 0.2) {
        abuseVertically = 0;
      }
    }

    return abuseVertically * multiplier;
  }

  public double calculateHorizontalViolationIncrease(
    User user,
    double predictedX,
    double predictedZ,
    boolean onLadder,
    boolean collidedWithBoat
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movementData = meta.movement();

    double motionX = movementData.motionX();
    double motionZ = movementData.motionZ();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movementData.positionX, movementData.positionZ,
      movementData.verifiedPositionX, movementData.verifiedPositionZ
    );
    double predictedDistanceMoved = Hypot.fast(predictedX, predictedZ);

    if (movementData.simulator() == Simulators.HORSE) {
//      user.player().sendMessage(distanceMoved + " " + predictedDistanceMoved);
      if (distanceMoved < predictedDistanceMoved) {
        return 0;
      }
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    boolean pushedByWaterFlow = movementData.pastPushedByWaterFlow <= 20;
    double legitimateDeviation;
    if (movementData.pastPlayerAttackPhysics <= 1) {
      legitimateDeviation = 0.01;
    } else {
      legitimateDeviation = 0.0007;
      if (distance > 0.0007) {
        boolean collides = Collision.nearBySolidBlock(player.getWorld(), movementData.boundingBox().growHorizontally(0.001));
        if (collides) {
          legitimateDeviation = distanceMoved < 0.04 ? 0.04 : 0.001;
        }
      }
    }

    if (movementData.collidedHorizontally && movementData.pastVelocity < 20) {
      legitimateDeviation = 0.027;
    }

    if (pushedByWaterFlow) {
      legitimateDeviation = 0.018;
    }

    if (movementData.currentlyInBlock && predictedDistanceMoved < distanceMoved * 1.3) {
      legitimateDeviation = predictedDistanceMoved;
    }

    // Firework
    if (movementData.fireworkTolerant) {
      legitimateDeviation = Math.max(legitimateDeviation, 0.8);
    }

    // Flying packet
    if (movementData.recentlyEncounteredFlyingPacket(2)) {
      if (movementData.onGround) {
        boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
        legitimateDeviation = lessThanExpected ? 0.115 : 0.005;
      } else {
        legitimateDeviation = 0.05;
      }
    }

    // Riptide
    if (movementData.pastRiptideSpin < 2) {
      legitimateDeviation = resolveRiptideDeviation(movementData);
    }

    boolean recentlySentFlying = movementData.recentlyEncounteredFlyingPacket(2);
    double baseMoveSpeed = movementData.baseMoveSpeed();
    boolean inLiquid = (movementData.pastWaterMovement < 20 && movementData.pastPushedByWaterFlow > 5) || movementData.inLava();

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      double baseSpeedMultiplier = inLiquid ? 0.3 : 0.7;
      if (lessThanExpected || distanceMoved < baseMoveSpeed * baseSpeedMultiplier) {
        legitimateDeviation = Math.max(legitimateDeviation, baseMoveSpeed * 0.7);
      }
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movementData.motionY() < 0 ? 0.4 : 0.2))) {
      legitimateDeviation = Math.max(distanceMoved, 0.2);
    }

    if (collidedWithBoat) {
      legitimateDeviation = Math.max(legitimateDeviation, 0.4);
    }

    if (movementData.physicsUnpredictableVelocityExpected) {
      Vector lastVelocity = movementData.lastVelocity;
      double velocityDistance = Hypot.fast(lastVelocity.getX(), lastVelocity.getZ());
      distance -= velocityDistance;
      legitimateDeviation = Math.max(legitimateDeviation, velocityDistance * 1.2 - distanceMoved);
    }

    if (movementData.sneaking || movementData.lastSneaking) {
      if (Math.abs(movementData.motionX()) < 0.05 || Math.abs(movementData.motionZ()) < 0.05) {
        legitimateDeviation = Math.max(legitimateDeviation, 0.1);
      }
    }

    if (movementData.pushedByEntity) {
      legitimateDeviation = Math.max(legitimateDeviation, 0.05);
    }

    double abuseHorizontally = Math.max(0, distance - legitimateDeviation);
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005 && abuseHorizontally > 0;

    if (inLiquid) {
      movedTooQuickly = movedTooQuickly && distanceMoved > baseMoveSpeed;
    }

    Pose pose = movementData.pose();
    if (pose == Pose.FALL_FLYING) {
      if (movementData.motionY() >= 0 && movementData.onGround) {
        abuseHorizontally *= 0.3;
      } else {
        abuseHorizontally *= 0.6;
      }
    }

    boolean movedTooQuicklyCheckable = distanceMoved > 0.3 || violationLevelData.physicsInvalidMovementsInRow >= 8;

    if (movedTooQuickly && movedTooQuicklyCheckable && !movementData.physicsUnpredictableVelocityExpected) {
      //noinspection UnnecessaryLocalVariable
      double vl = abuseHorizontally > 0.2 ? 1000 : Math.max(0.1, abuseHorizontally) * 300;
//      Bukkit.broadcastMessage(user.player().getName() + " moved too quickly: vl+" + vl + " abuse:" + abuseHorizontally + " | un:" + movementData.physicsUnpredictableVelocityExpected);
      return vl;
    }

    double multiplier = abuseHorizontally > 0.1 ? 20.0 : 10.0;
    return abuseHorizontally * multiplier;
  }

  private final static double RIPTIDE_TOLERANCE = 3.005;
  private final static double RIPTIDE_TOLERANCE_2 = 0.05;
  private final static double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(MovementMetadata movementData) {
    double riptideTolerance;
    if (movementData.onGround) {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_GROUND_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? RIPTIDE_TOLERANCE : RIPTIDE_TOLERANCE_2;
    }
    return riptideTolerance;
  }
}
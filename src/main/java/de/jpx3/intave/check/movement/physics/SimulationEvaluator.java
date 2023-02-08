package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.SplitMeUp;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;

@SplitMeUp
@Relocate
public final class SimulationEvaluator {
  private static final double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  @SplitMeUp
  public double calculateVerticalViolationLevelIncrease(
    User user,
    double predictedY,
    boolean onLadder,
    boolean collidedWithBoat
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();
    MovementMetadata movement = meta.movement();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movement.positionX, movement.positionZ,
      movement.verifiedPositionX, movement.verifiedPositionZ
    );
    Pose pose = movement.pose();
    double receivedMotionX = movement.motionX();
    double receivedMotionY = movement.motionY();
    double receivedMotionZ = movement.motionZ();
    double differenceY = Math.abs(receivedMotionY - predictedY);
    boolean accountedSkippedMovement = movement.recentlyEncounteredFlyingPacket(2);
    double verticalLegitimateDeviation = accountedSkippedMovement ? 0.01 : 0.00001;

    if (accountedSkippedMovement && movement.pastNearbyCollisionInaccuracy == 0) {
      if (Math.abs(movement.motionX()) < 0.05 && Math.abs(movement.motionZ()) < 0.05 && movement.motionY() < 0 && movement.motionY() > -0.4) {
        verticalLegitimateDeviation = 0.15;
      }
    }

    if (pose.height(user) < 1 && receivedMotionY <= 0 && accountedSkippedMovement) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
    }

    // MotionY calculations with sin/cos (FastMath affected)
    boolean fastMathAffected = pose == Pose.SWIMMING || pose == Pose.FALL_FLYING;
    if (fastMathAffected) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.001);
    }

    if ((movement.pastPushedByWaterFlow < 10 || movement.inLava()) && distanceMoved < 0.2) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.02);
    }

    // Riptide
    if (movement.pastRiptideSpin < 4) {
      verticalLegitimateDeviation = resolveRiptideDeviation(movement);
    }

    // Firework
    if (movement.fireworkRocketsTicks < 10 * movement.fireworkRocketsPower) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
    } else if (movement.fireworkRocketsTicks < 30 * movement.fireworkRocketsPower) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.5);
    }

    if (movement.shulkerYToleranceRemaining > 0 && // tick restriction
      (movement.positionY >= movement.lowestShulkerY - 1 && movement.positionY <= movement.highestShulkerY + 1) && // height restriction
      receivedMotionY - movement.jumpMotion() < 0.2 && // motion restriction
      (Math.abs(receivedMotionY) <= 0.5 || ((movement.positionY % 0.05) < 0.0001 && (Math.abs(receivedMotionY - movement.jumpMotion()) < 0.01 || (receivedMotionY <= 0 && receivedMotionY > -.8)))) // various other restrictions
    ) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      Set<Motion> toleratedPistonMotions = movement.toleratedPistonMotions;

//      player.sendMessage("tolerated2: " + toleratedPistonMotions);
      for (Motion toleratedPistonMotion : toleratedPistonMotions) {
        double toleratedPistonMotionY = toleratedPistonMotion.motionY;
        double toleratedPistonMotionDistance = Math.abs(toleratedPistonMotionY - receivedMotionY);
        if (toleratedPistonMotionDistance < 0.02) {
//          System.out.println("tolerated piston motion: " + toleratedPistonMotion);
          verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, toleratedPistonMotionY);
        }
      }
    }

    // spamming sneak under blocks
    // not a good solution, but it works
    if (protocol.applyModernCollider()) {
      double crouchingHeightGap = 1 - user.sizeOf(Pose.CROUCHING).height() % 1;
      double standingHeightGap = 1 - user.sizeOf(Pose.STANDING).height() % 1;
      boolean scuffed = false;
      // case 1: very likely to collide with block above
      if (Math.abs(receivedMotionY - crouchingHeightGap) < 0.01 || Math.abs(receivedMotionY - standingHeightGap) < 0.01) {
        scuffed = true;

        // case 2: jumping when Intave thinks it's not possible
      } else if (Math.abs(receivedMotionY - movement.jumpMotion()) < 0.01 && Math.abs(receivedMotionY - crouchingHeightGap) < 0.1) {
        scuffed = true;

        // case 3: I don't actually know what this is, it seems to work
      } else if (Math.abs(Math.abs(receivedMotionY - crouchingHeightGap) - movement.jumpMotion()) < 0.01) {
        scuffed = true;
      }
      boolean collides = Collision.present(player, BoundingBox.fromPosition(user, movement.positionX, movement.positionY + 0.0001, movement.positionZ)
        .expand(movement.motionX(), Math.abs(receivedMotionY + 0.1), movement.motionZ()));
//      player.sendMessage(scuffed + " " + movement.isSneaking() + " " + Math.abs(receivedMotionY - crouchingHeightGap) + " " + Math.abs(receivedMotionY - standingHeightGap));
      if (scuffed && collides) {
        differenceY = 0;
      }
    }

    if (movement.recentlyEncounteredFlyingPacket(3) && differenceY > 0.001) {
      boolean inLiquid = movement.pastWaterMovement <= 10 || movement.inLava();
      int allowedPackets = Hypot.fast(movement.motionX(), movement.motionZ()) < 0.03 ? 3 : 1;
      if (inLiquid || movement.physicsPacketRelinkFlyVL++ <= allowedPackets) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, inLiquid ? 0.1 : 0.03);
      }
    }

    if (movement.physicsUnpredictableVelocityExpected) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
    }

    if (collidedWithBoat && !movement.isInVehicle() && movement.motionY() < 0.605) {
      if (movement.enforceBoatStep) {
        if (movement.motionY() < 0.1) {
          verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 10);
        }
        movement.enforceBoatStep = false;
      } else if (movement.baseMotionY < 0) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 10);
        if (movement.motionY() > movement.jumpMotion()) {
          movement.enforceBoatStep = true;
        }
      }
    }

    boolean criticalWeb = receivedMotionY > -0.01
      && movement.pastInWeb < 10
      && !movement.inWater
      && !movement.inLava()
      && movement.positionY % 1 > 0.1
      && movement.pastExternalVelocity != 0;

    if (movement.inWeb) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, criticalWeb ? 0.000001 : 0.13);
    }

    if (movement.pastInWeb < 10 && !movement.inWeb && differenceY < 0.1) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
    }

    if (movement.recentlyEncounteredFlyingPacket(1) && movement.pastExternalVelocity <= 4) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.03);
    }

    verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, movement.estimatedAttachMovement());

    // Jump out of water
    if (movement.pastWaterMovement <= 3) {
      double liquidPositionY;
      if (protocol.waterUpdate()) {
        liquidPositionY = receivedMotionY + 0.6f - movement.positionY + movement.verifiedPositionY;
      } else {
        liquidPositionY = receivedMotionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
        player, movement.boundingBox(), receivedMotionX, liquidPositionY, receivedMotionZ
      );
      boolean maybeCollidedHorizontally = Collision.nearSolidBlock(player.getWorld(), movement.boundingBox().grow(0.2));
      if (maybeCollidedHorizontally && offsetPositionInLiquid && receivedMotionY < 0.4) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.7f);
      }
    }

    double abuseVertically = Math.max(0, differenceY - verticalLegitimateDeviation);
    boolean allowDeviation = fastMathAffected || movement.inLava() || movement.isInVehicle();
    double multiplier;

    if (abuseVertically > 0.1 && !allowDeviation) {
      multiplier = 5000;
    } else if (abuseVertically > 0.009 && !allowDeviation) {
      abuseVertically = Math.max(abuseVertically, 0.1);
      multiplier = 500;
    } else {
      multiplier = 100;
    }

    // rethink me
    if (pose == Pose.FALL_FLYING) {
      if (!movement.inWater && movement.pastWaterMovement <= 2 && Math.abs(receivedMotionY) < 0.1) {
        multiplier *= 0.01;
      } else if (movement.motionY() >= 0 && movement.onGround) {
        multiplier *= 0.1;
      } else {
        multiplier *= 0.25;
      }
    } else if (movement.pastElytraFlying < 4 && movement.motionY() < movement.jumpMotion()) {
      multiplier *= 0.1;
    }

    if (criticalWeb) {
      multiplier *= 40;
    }

    boolean justInPowderSnow = movement.pastInPowderSnow < 5;
    double maxLadderVel = justInPowderSnow ? LADDER_UPWARDS_MOTION * 1.5 : LADDER_UPWARDS_MOTION;
    if ((onLadder || justInPowderSnow) && movement.motionY() <= maxLadderVel && movement.motionY() >= -0.05) {
      abuseVertically = 0;
    }

    // Long teleport
    if (movement.pastLongTeleport <= 10 && movement.motionY() < -0.097 && movement.motionY() > -0.099) {
      double horizontalDistance = Hypot.fast(receivedMotionX, receivedMotionZ);
      if (horizontalDistance < 0.2) {
        abuseVertically = 0;
      }
    }
    return abuseVertically * multiplier;
  }

  @SplitMeUp
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
    MovementMetadata movement = meta.movement();
    ProtocolMetadata protocol = meta.protocol();

    double motionX = movement.motionX();
    double motionZ = movement.motionZ();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movement.positionX, movement.positionZ,
      movement.verifiedPositionX, movement.verifiedPositionZ
    );
    double predictedDistanceMoved = Hypot.fast(predictedX, predictedZ);

    if (movement.simulator() == Simulators.HORSE) {
//      user.player().sendMessage(distanceMoved + " " + predictedDistanceMoved);
      if (distanceMoved < predictedDistanceMoved) {
        return 0;
      }
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    boolean pushedByWaterFlow = movement.pastPushedByWaterFlow <= 20;
    double horizontalLegitimateDeviation;
    if (movement.pastPlayerAttackPhysics <= 1) {
      horizontalLegitimateDeviation = 0.01;
    } else {
      horizontalLegitimateDeviation = 0.0007;
      if (distance > 0.0007) {
        boolean collides = Collision.nearSolidBlock(player.getWorld(), movement.boundingBox().growHorizontally(0.001));
        if (collides) {
          horizontalLegitimateDeviation = distanceMoved < 0.04 ? 0.04 : 0.002;
        }
      }
    }

    if (movement.shulkerXToleranceRemaining > 0 || movement.shulkerZToleranceRemaining > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.3);
    }

    if (movement.shulkerYToleranceRemaining > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, Math.abs(movement.motionY()) < .3 ? .3 : .1);
    }

    if (movement.collidedHorizontally && movement.pastVelocity < 20) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.027);
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      Set<Motion> toleratedPistonMotions = movement.toleratedPistonMotions;

//      Set<Motion> toleratedPistonMotionsWithCrossSections = new HashSet<>();
//      for (Motion pistonMotion : toleratedPistonMotions) {
//        toleratedPistonMotionsWithCrossSections.add(pistonMotion);
//        for (Motion pistonMotion2 : toleratedPistonMotions) {
//          toleratedPistonMotionsWithCrossSections.add(pistonMotion.copy().add(pistonMotion2));
//        }
//      }

//      player.sendMessage("tolerated: " + toleratedPistonMotions);
      for (Motion toleratedPistonMotion : toleratedPistonMotions) {
        double toleratedPistonMotionX = toleratedPistonMotion.motionX;
        double toleratedPistonMotionZ = toleratedPistonMotion.motionZ;
        double toleratedPistonMotionDistance = MathHelper.resolveHorizontalDistance(
          toleratedPistonMotionX, toleratedPistonMotionZ,
          motionX, motionZ
        );
        if (toleratedPistonMotionDistance < 0.02) {
//          System.out.println("tolerated piston motion: " + toleratedPistonMotion);
          horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, toleratedPistonMotionDistance);
        }
      }
    }

    if (pushedByWaterFlow) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.018);
    }

    if (movement.currentlyInBlock && predictedDistanceMoved < distanceMoved * 1.3) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, predictedDistanceMoved);
    }

    // Firework
    if (movement.fireworkRocketsTicks < 30 * movement.fireworkRocketsPower) {
      // srsly who cares
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 3);
    }

    // Flying packet
    if (movement.recentlyEncounteredFlyingPacket(2)) {
      if (movement.onGround) {
        boolean lessThanExpected = distanceMoved <= predictedDistanceMoved + 0.02;
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, lessThanExpected ? 0.115 : 0.005);
      } else {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.05);
      }
      if (movement.pastNearbyCollisionInaccuracy == 0) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.05);
      }
    }

    // Riptide
    if (movement.pastRiptideSpin < 4) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, resolveRiptideDeviation(movement));
    }

    horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, movement.estimatedAttachMovement());

    boolean recentlySentFlying = movement.recentlyEncounteredFlyingPacket(2);
    double baseMoveSpeed = movement.baseMoveSpeed();
    boolean inLiquid = (movement.pastWaterMovement < 20 && movement.pastPushedByWaterFlow > 5) || movement.inLava();

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      double baseSpeedMultiplier = inLiquid ? 0.3 : (!movement.sprinting ? 0.5 : 0.7);
      boolean valid = movement.pastBlockPlacement > 9 || !movement.onGround();
      if (valid && (lessThanExpected || distanceMoved < baseMoveSpeed * baseSpeedMultiplier)) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, baseMoveSpeed * 0.7);
      }
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movement.motionY() < 0 ? 0.4 : 0.2))) {
      horizontalLegitimateDeviation = Math.max(distanceMoved, 0.2);
    }

    if (collidedWithBoat) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.4);
    }

    if (movement.physicsUnpredictableVelocityExpected) {
      Vector lastVelocity = movement.lastVelocity;
      double velocityDistance = Hypot.fast(lastVelocity.getX(), lastVelocity.getZ());
//      distance -= velocityDistance;
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.1);
//      player.sendMessage("Gave you " + (velocityDistance * 1.2 - distanceMoved) + " tolerance for velocity, plus extra " + velocityDistance + " for distance, " + distanceMoved + " for distance moved");
    }

    if (movement.sneaking || movement.lastSneaking) {
      double limit = 0;
      if ((Math.abs(movement.motionX()) < 0.08 || Math.abs(movement.motionZ()) < 0.08) || (movement.sprinting && protocol.cavesAndCliffsUpdate())) {
        boolean smallMovement = Math.abs(movement.motionX()) < 0.08 && Math.abs(movement.motionZ()) < 0.08 && movement.onGround();
        limit = movement.pastEdgeSneak <= 1 ? 0.12 : (smallMovement ? 0.099 : (movement.pastEdgeSneak < 10 ? 0.05 : 0.035));
        if (movement.motionY() >= 0.1 && protocol.cavesAndCliffsUpdate() && movement.pastEdgeSneak <= 1 && movement.sprinting && distanceMoved <= 0.5) {
          limit = 0.3;
        }
      } else {
        if (movement.pastEdgeSneak <= 3) {
          boolean smallMovement = (Math.abs(movement.motionX()) < 0.099 && Math.abs(movement.motionZ()) < 0.2) || (Math.abs(movement.motionZ()) < 0.099 && Math.abs(movement.motionX()) < 0.2) && movement.onGround();
//          player.sendMessage(movement.motionX() + " " + movement.motionZ() + " " + smallMovement);
          limit = smallMovement ? 0.2 : 0.02;
        }
      }
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, limit);
    }

    if (movement.pushedByEntity) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.05);
    }

    double abuseHorizontally = Math.max(0, distance - horizontalLegitimateDeviation);
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005 && abuseHorizontally > 0;

    if (inLiquid) {
      movedTooQuickly = movedTooQuickly && distanceMoved > baseMoveSpeed;
    }

    Pose pose = movement.pose();
    boolean flewWithElytra = movement.pastElytraFlying <= 3;

    if (pose == Pose.FALL_FLYING) {
      if (!movement.inWater && movement.pastWaterMovement <= 2 && distance < 0.3) {
        abuseHorizontally *= 0.2;
      } else if (movement.motionY() >= 0 && movement.onGround) {
        abuseHorizontally *= 0.3;
      } else {
        abuseHorizontally *= 0.6;
      }
    } else if (flewWithElytra) {
      abuseHorizontally *= 0.1;
    }

    boolean movedTooQuicklyCheckable = (distanceMoved > 0.3 || violationLevelData.physicsInvalidMovementsInRow >= 8)
      && !flewWithElytra;

    if (movedTooQuickly && movedTooQuicklyCheckable && !movement.physicsUnpredictableVelocityExpected) {
      //noinspection UnnecessaryLocalVariable
      double vl = abuseHorizontally > 0.2 ? 1000 : Math.max(30, abuseHorizontally * 300);
//      Bukkit.broadcastMessage(user.player().getName() + " moved too quickly: vl+" + vl + " abuse:" + abuseHorizontally + " | un:" + movement.physicsUnpredictableVelocityExpected);
      return vl;
    }
    double multiplier = abuseHorizontally > 0.1 ? 20.0 : 10.0;
    return abuseHorizontally * multiplier;
  }

//  private static final double RIPTIDE_TOLERANCE = 3.005;
  private static final double RIPTIDE_TOLERANCE_2 = 0.05;
  private static final double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(MovementMetadata movementData) {
    double riptideTolerance;
    double imminentSpinTolerance = movementData.highestLocalRiptideLevel + 1.005;
    if (movementData.onGroundWithRiptide) {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? imminentSpinTolerance * 1.5 : RIPTIDE_GROUND_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.pastRiptideSpin == 0 ? imminentSpinTolerance : RIPTIDE_TOLERANCE_2;
    }
    return riptideTolerance;
  }
}
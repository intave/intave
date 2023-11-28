package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.annotate.refactoring.SplitMeUp;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Player;

import static java.lang.Math.abs;

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
    double differenceY = abs(receivedMotionY - predictedY);
    boolean accountedSkippedMovement = movement.receivedFlyingPacketIn(2);
    double verticalLegitimateDeviation = accountedSkippedMovement ? 0.01 : 0.00001;

    if (accountedSkippedMovement) {
      if (abs(movement.motionX()) < 0.05 && abs(movement.motionZ()) < 0.05 && movement.motionY() < 0 && movement.motionY() > -0.4) {
        verticalLegitimateDeviation = movement.pastNearbyCollisionInaccuracy == 0 ? 0.15 : (0.08);
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
        (abs(receivedMotionY) <= 0.5 || ((movement.positionY % 0.05) < 0.0001 && (abs(receivedMotionY - movement.jumpMotion()) < 0.01 || (receivedMotionY <= 0 && receivedMotionY > -.8)))) // various other restrictions
    ) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      // Check if the player box is inside the piston box
      if (movement.pistonCollisionArea != null && movement.pistonCollisionArea.intersectsWith(movement.boundingBox())) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, movement.pistonVerticalAllowance);
      }
    }

    // spamming sneak under blocks
    // not a good solution, but it works (sometimes)
    if (protocol.applyModernCollider()) {
      double crouchingHeightGap = 1 - user.sizeOf(Pose.CROUCHING).height() % 1;
      double standingHeightGap = 1 - user.sizeOf(Pose.STANDING).height() % 1;
      boolean scuffed = false;
      // case 1: very likely to collide with block above
      if (abs(receivedMotionY - crouchingHeightGap) < 0.01 || abs(receivedMotionY - standingHeightGap) < 0.01) {
        scuffed = true;

        // case 2: jumping when Intave thinks it's not possible
      } else if (abs(receivedMotionY - movement.jumpMotion()) < 0.01 && abs(receivedMotionY - crouchingHeightGap) < 0.1) {
        scuffed = true;

        // case 3: I don't actually know what this is, it seems to work
      } else if (abs(abs(receivedMotionY - crouchingHeightGap) - movement.jumpMotion()) < 0.01) {
        scuffed = true;
      }
      boolean collides = Collision.present(player, BoundingBox.fromPosition(user, movement, movement.positionX, movement.positionY + 0.0001, movement.positionZ)
          .expand(movement.motionX(), abs(receivedMotionY + 0.1), movement.motionZ()));
//      player.sendMessage(scuffed + " " + movement.isSneaking() + " " + Math.abs(receivedMotionY - crouchingHeightGap) + " " + Math.abs(receivedMotionY - standingHeightGap));
      if (scuffed && collides) {
        differenceY = 0;
      }
    }

    if (movement.receivedFlyingPacketIn(3) && differenceY > 0.001 && protocol.combatUpdate() && movement.pastBlockPlacement > 10) {
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
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, /*criticalWeb ? 0.000001 : */0.13);
    }

    if (movement.pastInWeb < 10 && !movement.inWeb && differenceY < 0.1) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
    }

    if (movement.receivedFlyingPacketIn(1) && movement.pastExternalVelocity <= 4) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.03);
    }

    if (movement.receivedFlyingPacketIn(2) && (movement.inWater() || movement.inLava())) {
      if (Math.abs(movement.motionY()) < 0.1 &&
        Math.abs(movement.motionX()) < 0.1 &&
        Math.abs(movement.motionZ()) < 0.1 &&
        Math.abs(predictedY) < 0.1 &&
        movement.pastExternalVelocity > 8
      ) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
      }
    }

    verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, movement.estimatedAttachMovement());

    // Jump out of water
    if (movement.pastWaterMovement <= 3) {
      double liquidMotionY;
      if (protocol.waterUpdate()) {
        liquidMotionY = receivedMotionY + 0.6f - movement.positionY + movement.verifiedPositionY;
      } else {
        liquidMotionY = receivedMotionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
        player, movement.boundingBox(), receivedMotionX, liquidMotionY, receivedMotionZ
      );
      boolean maybeCollidedHorizontally = Collision.nearSolidBlock(user, movement.boundingBox().grow(0.2));
      if (maybeCollidedHorizontally && offsetPositionInLiquid && receivedMotionY < 0.4) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.7f);
      }
    }

    // Sometimes shit happens
    if (movement.ticksSneaking <= 1 && (movement.onGround() || movement.lastOnGround()) && movement.motionY() <= 0 && movement.lastSneaking) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.08f);
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
      if (!movement.inWater && movement.pastWaterMovement <= 2 && abs(receivedMotionY) < 0.1) {
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
//      multiplier *= 40;
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
    double predictedX, double predictedZ,
    boolean onLadder, boolean collidedWithBoat
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    MovementMetadata movement = meta.movement();
    ProtocolMetadata protocol = meta.protocol();

    double motionX = movement.motionX();
    double motionY = movement.motionY();
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
      horizontalLegitimateDeviation = 0.005;
    } else {
      horizontalLegitimateDeviation = 0.0007;
      if (distance > 0.0007) {
        boolean collides = Collision.nearSolidBlock(user, movement.boundingBox().growHorizontally(0.001));
        if (collides) {
          horizontalLegitimateDeviation = distanceMoved < 0.04 ? 0.04 : 0.002;
        }
      }
      if (user.meta().protocol().beeUpdate() && (abs(motionX) < 0.09 || abs(motionZ) < 0.09)) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.009);
      }
    }

    if ((movement.shulkerXToleranceRemaining > 0 || movement.shulkerZToleranceRemaining > 0) && abs(motionY) < 0.5 && abs(motionZ) < 0.5) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.3);
    }

    if (movement.shulkerYToleranceRemaining > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, abs(movement.motionY()) < .3 ? .3 : .1);
    }

    if (movement.collidedHorizontally && movement.pastVelocity < 20) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.027);
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      // Check if the player box is inside the piston box
      if (movement.pistonCollisionArea != null && movement.pistonCollisionArea.intersectsWith(movement.boundingBox())) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, movement.pistonHorizontalAllowance);
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
    if (movement.receivedFlyingPacketIn(2)) {
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

    boolean recentlySentFlying = movement.receivedFlyingPacketIn(2);
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
//      Vector lastVelocity = movement.lastVelocity;
//      double velocityDistance = Hypot.fast(lastVelocity.getX(), lastVelocity.getZ());
//      distance -= velocityDistance;
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.1);
//      player.sendMessage("Gave you " + (velocityDistance * 1.2 - distanceMoved) + " tolerance for velocity, plus extra " + velocityDistance + " for distance, " + distanceMoved + " for distance moved");
    }

    if (movement.ticksSneaking <= 1 && movement.sneaking || movement.lastSneaking) {
      double limit = 0;
      if ((abs(movement.motionX()) < 0.08 || abs(movement.motionZ()) < 0.08) || (movement.sprinting && protocol.cavesAndCliffsUpdate())) {
        boolean smallMovement = abs(movement.motionX()) < 0.08 && abs(movement.motionZ()) < 0.08 && movement.onGround();
        limit = movement.pastEdgeSneak <= 1 ? 0.12 : (smallMovement ? 0.099 : (movement.pastEdgeSneak < 10 ? 0.05 : 0.035));
        if (movement.motionY() >= 0.1 && protocol.cavesAndCliffsUpdate() && movement.pastEdgeSneak <= 1 && movement.sprinting && distanceMoved <= 0.5) {
          limit = 0.4;
        }
        if (abs(movement.motionY()) < 0.001) {
          limit = 0.08;
        }
        if (movement.pastEdgeSneak <= 3 && !protocol.flyingPacketsAreSent()) {
          limit = Math.max(limit, 0.07);
        }
//        player.sendMessage("Gave you " + limit + " tolerance for edge sneak");
      } else {
        if (movement.pastEdgeSneak <= 3 || (movement.pastEdgeSneak <= 10 && movement.onGround() && abs(motionY) < 0.01)) {
          boolean smallMovement = (abs(movement.motionX()) < 0.099 && abs(movement.motionZ()) < 0.21) || (abs(movement.motionZ()) < 0.099 && abs(movement.motionX()) < 0.21) && movement.onGround();
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
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

package de.jpx3.intave.check.movement.physics.evaluation;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.check.movement.physics.environment.MovementCharacteristics;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulators;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;

import java.util.Set;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.*;
import static java.lang.Math.abs;

public final class DefaultSimulationEvaluator implements SimulationEvaluator {
  private static final double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  @Override
  public double calculateViolationIncrease(
    User user,
    SimulationEnvironment movement,
    double predictedX,
    double predictedY,
    double predictedZ,
    double motionX,
    double motionY,
    double motionZ,
    boolean onLadder,
    boolean collidedWithEntity,
    Set<? super EvaluationTag> tags
  ) {
	  MetadataBundle meta = user.meta();
    ViolationMetadata violationLevelData = meta.violationLevel();
    ProtocolMetadata protocol = meta.protocol();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movement.positionX(), movement.positionZ(),
      movement.verifiedLastPositionX(), movement.verifiedLastPositionZ()
    );
    Pose pose = movement.pose();
    double motionXDifference = abs(motionX - predictedX);
    double motionYDifference = abs(motionY - predictedY);
    double motionZDifference = abs(motionZ - predictedZ);
    double predictedDistanceMoved = Hypot.fast(predictedX, predictedZ);
    double horizontalDistance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    boolean checkY = motionYDifference > 0;
    boolean checkXZ = motionXDifference > 0 || motionZDifference > 0;
    double verticalViolationIncrease = 0;
    double horizontalViolationIncrease = 0;

    if (checkY) {
      Timings.CHECK_PHYSICS_EVAL_VERTICAL.start();
      double motionYTolerance;
      boolean accountedSkippedMovement = movement.receivedFlyingPacketIn(2);
      motionYTolerance = accountedSkippedMovement ? 0.01 : 0.00001;

      if (accountedSkippedMovement) {
        if (abs(motionX) < 0.05 && abs(motionZ) < 0.05 && motionY < 0 && motionY > -0.4) {
          boolean pastCollision = movement.ticksPast(NEARBY_COLLISION_INACCURACY) == 0 && !movement.inWeb();
          motionYTolerance = pastCollision ? 0.15 : (0.08);
          tags.add(EvaluationTag.FLYING);
          if (pastCollision) {
            tags.add(EvaluationTag.COLLISION);
          }
        }
      }

      if (pose.height(user, movement) < 1 && motionY <= 0 && accountedSkippedMovement) {
        motionYTolerance = Math.max(motionYTolerance, 0.1);
        tags.add(EvaluationTag.CROUCHING_BOX);
      }

      // MotionY calculations with sin/cos (FastMath affected)
      boolean fastMathAffected = pose == Pose.SWIMMING || pose == Pose.FALL_FLYING;
      if (fastMathAffected) {
        motionYTolerance = Math.max(motionYTolerance, 0.001);
      }

      if ((movement.ticksPast(WATERFLOW_PUSH) < 10 || movement.inLava()) && distanceMoved < 0.2) {
        motionYTolerance = Math.max(motionYTolerance, 0.02);
        tags.add(EvaluationTag.WATERFLOW);
      }

      // Riptide
      if (movement.ticksPast(RIPTIDE_SPIN) < 4) {
        motionYTolerance = resolveRiptideDeviation(movement);
        tags.add(EvaluationTag.RIPTIDE);
      }

      // Before 1.17, fireworks are weird
      if (!protocol.fireworkBoostTicksAfterPlayer()
        && movement.ticksPast(FIREWORK_ROCKETS) < 10 * movement.fireworkRocketsPower()) {
        motionYTolerance = Math.max(motionYTolerance, 1);
        tags.add(EvaluationTag.FIREWORK);
      } else if (!protocol.fireworkBoostTicksAfterPlayer()
        && movement.ticksPast(FIREWORK_ROCKETS) < 30 * movement.fireworkRocketsPower()) {
        motionYTolerance = Math.max(motionYTolerance, 0.75);
        tags.add(EvaluationTag.FIREWORK);
      }

      if (movement.shulkerYToleranceRemaining() > 0 && // tick restriction
        (movement.positionY() >= movement.lowestShulkerY() - 1 && movement.positionY() <= movement.highestShulkerY() + 1) && // height restriction
        motionY - movement.jumpMotion() < 0.2 && // motion restriction
        (abs(motionY) <= 0.5 || ((movement.positionY() % 0.05) < 0.0001 && (abs(motionY - movement.jumpMotion()) < 0.01 || (motionY <= 0 && motionY > -.8)))) // various other restrictions
      ) {
        motionYTolerance = Math.max(motionYTolerance, 1);
        tags.add(EvaluationTag.SHULKER);
      }

      if (movement.pistonMotionToleranceRemaining() > 0) {
        // Check if the player box is inside the piston box
        BoundingBox pistonCollisionArea = movement.pistonCollisionArea();
        if (pistonCollisionArea != null && pistonCollisionArea.intersectsWith(movement.boundingBox())) {
          motionYTolerance = Math.max(motionYTolerance, movement.pistonVerticalAllowance());
          tags.add(EvaluationTag.PISTON);
        }
      }

      // spamming sneak under blocks
      // not a good solution, but it works (sometimes)
      if (protocol.applyModernCollider()) {
        double crouchingHeightGap = 1 - user.sizeOf(Pose.CROUCHING).height() % 1;
        double standingHeightGap = 1 - user.sizeOf(Pose.STANDING).height() % 1;
        boolean scuffed = false;
        // case 1: very likely to collide with block above
        if (abs(motionY - crouchingHeightGap) < 0.01 || abs(motionY - standingHeightGap) < 0.01) {
          scuffed = true;

          // case 2: jumping when Intave thinks it's not possible
        } else if (abs(motionY - movement.jumpMotion()) < 0.01 && abs(motionY - crouchingHeightGap) < 0.1) {
          scuffed = true;

          // case 3: I don't actually know what this is, it seems to work
        } else if (abs(abs(motionY - crouchingHeightGap) - movement.jumpMotion()) < 0.01) {
          scuffed = true;
        }
        boolean collides = Collision.present(user, movement, BoundingBox.fromPosition(user, movement, movement.positionX(), movement.positionY() + 0.0001, movement.positionZ())
          .expand(motionX, abs(motionY + 0.1), motionZ));
//      player.sendMessage(scuffed + " " + movement.isSneaking() + " " + Math.abs(motionY - crouchingHeightGap) + " " + Math.abs(motionY - standingHeightGap));
        if (scuffed && collides) {
          motionYDifference = 0;
          tags.add(EvaluationTag.CROUCHING_BOX);
        }
      }

      if (movement.receivedFlyingPacketIn(3) && motionYDifference > 0.001 && movement.ticksPast(BLOCK_PLACEMENT) > 10) {
        boolean verticalInLiquid = movement.ticksPast(IN_WATER) <= 10 || movement.inLava();
        int allowedPackets = Hypot.fast(motionX, motionZ) < 0.03 ? 3 : 1;
        int physicsPacketRelinkFlyVL = movement.physicsPacketRelinkFlyVL();
        movement.setPhysicsPacketRelinkFlyVL(physicsPacketRelinkFlyVL + 1);
        if (verticalInLiquid || physicsPacketRelinkFlyVL <= allowedPackets) {
          motionYTolerance = Math.max(motionYTolerance, verticalInLiquid ? 0.12 : 0.03);
          tags.add(EvaluationTag.FLYING);
          if (verticalInLiquid) {
            tags.add(EvaluationTag.WATER_FLY);
          }
        }
      }

      if (movement.ticksPast(BLOCK_PLACEMENT) < 10 && movement.receivedFlyingPacketIn(3)) {
        motionYTolerance = Math.max(motionYTolerance, 0.03);
//      player.sendMessage(movement.receivedFlyingPacketIn(3) + " " + differenceY + " " + movement.past(BLOCK_PLACEMENT));
        tags.add(EvaluationTag.FLYING);
      }

      if (movement.physicsUnpredictableVelocityExpected()) {
        motionYTolerance = Math.max(motionYTolerance, 0.1);
        tags.add(EvaluationTag.VELOCITY_FLYING);
      }

      if (collidedWithEntity && !movement.isInVehicle() && motionY < 0.605) {
        if (movement.enforceBoatStep()) {
          if (motionY < 0.1) {
            motionYTolerance = Math.max(motionYTolerance, 1);
            tags.add(EvaluationTag.COLLISION);
          }
          movement.setEnforceBoatStep(false);
        } else if (movement.baseMotionY() <= 0) {
          motionYTolerance = Math.max(motionYTolerance, 1);
          if (motionY > movement.jumpMotion()) {
            movement.setEnforceBoatStep(true);
          }
          tags.add(EvaluationTag.COLLISION);
        }
      }

      boolean criticalWeb = motionY > -0.01
        && movement.ticksPast(IN_WEB) < 10
        && !movement.inWater()
        && !movement.inLava()
        && movement.positionY() % 1 > 0.1
        && movement.ticksPast(EXTERNAL_VELOCITY) != 0;

      boolean movingUpwardsInWeb = movement.ticks(IN_WEB) > 2 && motionY >= 0 && !movement.onGround() && movement.ticksPast(EXTERNAL_VELOCITY) > 3;

      if (movement.inWeb()) {
        motionYTolerance = Math.max(motionYTolerance, movingUpwardsInWeb ? 0.00001 :/*criticalWeb ? 0.000001 : */0.13);
        tags.add(EvaluationTag.WEB);
      }

      if (movement.ticksPast(IN_WEB) < 10 && !movement.inWeb() && motionYDifference < 0.1) {
        motionYTolerance = Math.max(motionYTolerance, 0.1);
        tags.add(EvaluationTag.WEB);
      }

      if (movement.receivedFlyingPacketIn(1) && movement.ticksPast(EXTERNAL_VELOCITY) <= 4) {
        motionYTolerance = Math.max(motionYTolerance, 0.03);
        tags.add(EvaluationTag.FLYING);
        tags.add(EvaluationTag.VELOCITY_FLYING);
      }

      if (movement.receivedFlyingPacketIn(2) && (movement.inWater() || movement.inLava())) {
        if (abs(motionY) < 0.1 &&
          abs(motionX) < 0.1 &&
          abs(motionZ) < 0.1 &&
          abs(predictedY) < 0.1 &&
          movement.ticksPast(EXTERNAL_VELOCITY) > 8
        ) {
          motionYTolerance = Math.max(motionYTolerance, 0.1);
          tags.add(EvaluationTag.FLYING);
        }
      }

      if (movement.pose() == Pose.SWIMMING && !movement.inWater()) {
//      if (distanceMoved < 0.1) {
        motionYTolerance = Math.max(motionYTolerance, 0.08);
        tags.add(EvaluationTag.FLYING_ON_GROUND);
//      }
//      user.sendMessage("Swimming Y: " + formatDouble(distanceMoved, 4) + " | " +
//        formatDouble(differenceY, 4) + " (" + formatDouble(verticalLegitimateDeviation, 4) + ")");
      }

      if (movement.inWater() && movement.ticks(IN_WATER) < 2) {
        motionYTolerance = Math.max(motionYTolerance, 0.05);
        tags.add(EvaluationTag.WATERFLOW);
      }

      if (movement.inWater() && !movement.isSprinting() && distanceMoved < 0.1 && motionY > -0.06 && motionY < 0) {
        motionYTolerance = Math.max(motionYTolerance, 0.01);
        tags.add(EvaluationTag.WATERFLOW);
      }

      if (movement.ticksPast(VEHICLE_ATTACHMENT) <= 1 && movement.isInVehicle()) {
        Entity vehicle = movement.vehicle();
        BoundingBox grownBoatBox = vehicle.boundingBox().grow(0.5);
        BoundingBox nextPlayerBox = BoundingBox.fromPosition(
          user, movement, movement.positionX(), movement.positionY(), movement.positionZ()
        );
        BoundingBox currentPlayerBox = movement.boundingBox();
        boolean attachAllowed = grownBoatBox.intersectsWith(nextPlayerBox) || grownBoatBox.intersectsWith(currentPlayerBox);
        double moveToAttachMoveDelta = abs(MathHelper.hypot3d(motionX, motionY, motionZ) - distanceMoved);
        boolean movementInLineWithDistance = moveToAttachMoveDelta < 0.5;
//      player.sendMessage(attachAllowed + " " + movementInLineWithDistance + " " + moveToAttachMoveDelta);
        if (attachAllowed /*&& movementInLineWithDistance*/) {
          motionYTolerance = Math.max(motionYTolerance, 5);
          tags.add(EvaluationTag.ATTACH);
        }
      }

      if (movement.ticksPast(VEHICLE_DETACHMENT) <= 2 && abs(motionY) < 0.1) {
        motionYTolerance = Math.max(motionYTolerance, 0.3);
      }

      // Jump out of water
      if (movement.ticksPast(IN_WATER) <= 3 || movement.ticksPast(IN_LAVA) <= 3) {
        double liquidMotionY;
        if (protocol.aquaticUpdate()) {
          liquidMotionY = motionY + 0.6f - movement.positionY() + movement.verifiedLastPositionY();
        } else {
          liquidMotionY = motionY + 0.3f;
        }
        boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
          user, movement, movement.boundingBox(), motionX, liquidMotionY, motionZ
        );
        boolean maybeCollidedHorizontally = Collision.nearSolidBlock(user, movement.boundingBox().grow(0.2, 0.5, 0.2));
        boolean targetMotion = abs(motionY - 0.3) < 0.001 || abs(motionY - 0.34) < 0.001 || abs(motionY - 0.2470) < 0.001;
        if (maybeCollidedHorizontally && offsetPositionInLiquid && targetMotion) {
          motionYTolerance = Math.max(motionYTolerance, 0.7f);
          tags.add(EvaluationTag.WATERFLOW);
        }
      }

      // Sometimes shit happens
      if (movement.ticks(SNEAKING) <= 1 && !movement.inWater() && !movement.inWeb() && (movement.onGround() || movement.lastOnGround()) && motionY <= 0 && motionY >= -0.5 && movement.lastSneaking()) {
        motionYTolerance = Math.max(motionYTolerance, 0.08f);
        tags.add(EvaluationTag.SNEAKING);
      }

      double abuseVertically = Math.max(0, motionYDifference - motionYTolerance);
      boolean allowDeviation = fastMathAffected || movement.inLava() || movement.isInVehicle();
      double verticalMultiplier;

      if (abuseVertically > 0.1 && !allowDeviation) {
        verticalMultiplier = 5000;
      } else if (abuseVertically > 0.009 && !allowDeviation) {
        abuseVertically = Math.max(abuseVertically, 0.1);
        verticalMultiplier = 500;
      } else {
        verticalMultiplier = 100;
      }

      // rethink me
      if (criticalWeb) {
//      verticalMultiplier *= 40;
      }

      boolean justInPowderSnow = movement.ticksPast(IN_POWDER_SNOW) < 5;
      double maxLadderVel = justInPowderSnow ? LADDER_UPWARDS_MOTION * 1.5 : LADDER_UPWARDS_MOTION;
      if ((onLadder || justInPowderSnow) && motionY <= maxLadderVel && (motionY >= -0.09 || Math.abs(motionY - -0.150) < 0.001)) {
        abuseVertically = 0;
        tags.add(EvaluationTag.LADDER);
      }

      // Long teleport
      if (movement.ticksPast(LONG_TELEPORT) <= 10 && motionY < -0.097 && motionY > -0.099) {
        double receivedHorizontalDistance = Hypot.fast(motionX, motionZ);
        if (receivedHorizontalDistance < 0.2) {
          abuseVertically = 0;
          tags.add(EvaluationTag.CHUNK_LOADING);
        }
      }

      verticalViolationIncrease = abuseVertically * verticalMultiplier;
      Timings.CHECK_PHYSICS_EVAL_VERTICAL.stop();
    }

    if (checkXZ) {
      Timings.CHECK_PHYSICS_EVAL_HORIZONTAL.start();
      if (movement.simulator() == Simulators.HORSE && distanceMoved < predictedDistanceMoved) {
        Timings.CHECK_PHYSICS_EVAL_HORIZONTAL.stop();
        return verticalViolationIncrease;
      }

      boolean pushedByWaterFlow = movement.ticksPast(WATERFLOW_PUSH) <= 20;
      double motionXTolerance;
      double motionZTolerance;
      motionXTolerance = 0.0007;
      motionZTolerance = 0.0007;
      if (horizontalDistance > 0.0007) {
        boolean collides = Collision.nearSolidBlock(user, movement.boundingBox().growHorizontally(0.001)) && !movement.inWeb();
        if (collides) {
          double collisionTolerance = distanceMoved < 0.04 ? 0.04 : 0.003;
          motionXTolerance = Math.max(motionXTolerance, collisionTolerance);
          motionZTolerance = Math.max(motionZTolerance, collisionTolerance);
          tags.add(EvaluationTag.COLLISION);
        }
      }
      if (protocol.beeUpdate() && protocol.flyingPacketsCausePositionUncertainty() && (abs(motionX) < 0.09 || abs(motionZ) < 0.09)) {
        motionXTolerance = Math.max(motionXTolerance, 0.009);
        motionZTolerance = Math.max(motionZTolerance, 0.009);
        tags.add(EvaluationTag.FLYING);
      }

      if (abs(motionY) < 0.5) {
        if (movement.shulkerXToleranceRemaining() > 0 && abs(motionX) < 0.5) {
          motionXTolerance = Math.max(motionXTolerance, 0.3);
          tags.add(EvaluationTag.SHULKER);
        }
        if (movement.shulkerZToleranceRemaining() > 0 && abs(motionZ) < 0.5) {
          motionZTolerance = Math.max(motionZTolerance, 0.3);
          tags.add(EvaluationTag.SHULKER);
        }
      }

      if (movement.shulkerYToleranceRemaining() > 0) {
        double shulkerYTolerance = abs(motionY) < .3 ? .3 : .1;
        motionXTolerance = Math.max(motionXTolerance, shulkerYTolerance);
        motionZTolerance = Math.max(motionZTolerance, shulkerYTolerance);
        tags.add(EvaluationTag.SHULKER);
      }
      if (movement.collidedHorizontally()) {
        motionXTolerance = Math.max(motionXTolerance, 0.027);
        motionZTolerance = Math.max(motionZTolerance, 0.027);
        tags.add(EvaluationTag.COLLISION);
      }

      if (movement.pistonMotionToleranceRemaining() > 0) {
        // Check if the player box is inside the piston box
        BoundingBox pistonCollisionArea = movement.pistonCollisionArea();
        if (pistonCollisionArea != null && pistonCollisionArea.intersectsWith(movement.boundingBox())) {
          motionXTolerance = Math.max(motionXTolerance, movement.pistonHorizontalAllowance());
          motionZTolerance = Math.max(motionZTolerance, movement.pistonHorizontalAllowance());
          tags.add(EvaluationTag.PISTON);
        }
      }

      if (pushedByWaterFlow) {
        motionXTolerance = Math.max(motionXTolerance, 0.018);
        motionZTolerance = Math.max(motionZTolerance, 0.018);
        tags.add(EvaluationTag.WATERFLOW);
      }

      if (movement.pose() == Pose.SWIMMING && !movement.inWater()) {
        double limit = Math.max(0.1, movement.baseMoveSpeed() * 0.33);
        if (distanceMoved < limit) {
          motionXTolerance = Math.max(motionXTolerance, 0.14);
          motionZTolerance = Math.max(motionZTolerance, 0.14);
          tags.add(EvaluationTag.FLYING_ON_GROUND);
        }
//      user.sendMessage("Swimming XZ: " + formatDouble(distanceMoved, 4) + "/"+formatDouble(limit, 4)+" | " +
//        formatDouble(distance, 4) + " (" + formatDouble(Math.max(motionXTolerance, motionZTolerance), 4) + ")");
      } else if (movement.inWater() && movement.pose() != Pose.SWIMMING) {
        if (distanceMoved < 0.1 && motionY > -0.06 && motionY < 0) {
          motionXTolerance = Math.max(motionXTolerance, 0.01);
          motionZTolerance = Math.max(motionZTolerance, 0.01);
          tags.add(EvaluationTag.WATERFLOW);
        }
      }

      if (movement.currentlyInBlock() && !movement.inWeb() && predictedDistanceMoved < distanceMoved * 1.3) {
        motionXTolerance = Math.max(motionXTolerance, predictedDistanceMoved);
        motionZTolerance = Math.max(motionZTolerance, predictedDistanceMoved);
        tags.add(EvaluationTag.COLLISION);
      }

      // Before 1.17, client entity iteration does not preserve insertion order.
      if (!protocol.fireworkBoostTicksAfterPlayer()
        && movement.ticksPast(FIREWORK_ROCKETS) < 30 * movement.fireworkRocketsPower()) {
        motionXTolerance = Math.max(motionXTolerance, 3);
        motionZTolerance = Math.max(motionZTolerance, 3);
        tags.add(EvaluationTag.FIREWORK);
      }

      // Flying packet
      double flyingLimit = 0.05;
      if (movement.receivedFlyingPacketIn(2)) {
        if (movement.onGround()) {
          boolean specialWeb = movement.inWeb();
          boolean lessThanExpected = distanceMoved < 0.15;
          double flyingTolerance = specialWeb ? 0.1 : (lessThanExpected ? 0.115 : flyingLimit);
          motionXTolerance = Math.max(motionXTolerance, flyingTolerance);
          motionZTolerance = Math.max(motionZTolerance, flyingTolerance);
          tags.add(EvaluationTag.FLYING_ON_GROUND);
        } else {
          motionXTolerance = Math.max(motionXTolerance, flyingLimit);
          motionZTolerance = Math.max(motionZTolerance, flyingLimit);
          tags.add(EvaluationTag.FLYING);
        }
        if (movement.ticksPast(NEARBY_COLLISION_INACCURACY) == 0) {
          motionXTolerance = Math.max(motionXTolerance, flyingLimit);
          motionZTolerance = Math.max(motionZTolerance, flyingLimit);
          tags.add(EvaluationTag.FLYING);
          tags.add(EvaluationTag.COLLISION);
        }
      }

      // Riptide
      if (movement.ticksPast(RIPTIDE_SPIN) < 4) {
        double riptideTolerance = resolveRiptideDeviation(movement);
        motionXTolerance = Math.max(motionXTolerance, riptideTolerance);
        motionZTolerance = Math.max(motionZTolerance, riptideTolerance);
//      player.sendMessage(Collision.present(player, BoundingBox.fromPosition(user, movement, movement.positionX, movement.positionY, movement.positionZ).grow(0.1)) + " " + movement.past(RIPTIDE_SPIN));
        tags.add(EvaluationTag.RIPTIDE);
      }

      if (movement.ticksPast(VEHICLE_ATTACHMENT) <= 1 && movement.isInVehicle()) {
        Entity vehicle = movement.vehicle();
        BoundingBox grownBoatBox = vehicle.boundingBox().grow(0.5);
        BoundingBox nextPlayerBox = BoundingBox.fromPosition(
          user, movement, movement.positionX(), movement.positionY(), movement.positionZ()
        );
        BoundingBox currentPlayerBox = movement.boundingBox();
        boolean attachAllowed = grownBoatBox.intersectsWith(nextPlayerBox) || grownBoatBox.intersectsWith(currentPlayerBox);
//      double moveToAttachMoveDelta = abs(movement.motion().length() - distanceMoved);
//      boolean movementInLineWithDistance = moveToAttachMoveDelta < 0.5;
        if (attachAllowed /*&& movementInLineWithDistance*/) {
          motionXTolerance = Math.max(motionXTolerance, 5);
          motionZTolerance = Math.max(motionZTolerance, 5);
          tags.add(EvaluationTag.ATTACH);
          if (horizontalDistance > 0.1) {
            movement.setMotionResetX(true);
            movement.setMotionResetZ(true);
          }
        }
      }

      boolean recentlySentFlying = movement.receivedFlyingPacketIn(2);
      double baseMoveSpeed = movement.baseMoveSpeed();
      boolean inLiquid = (movement.ticksPast(IN_WATER) < 20 && movement.ticksPast(WATERFLOW_PUSH) > 5) || movement.inLava();

      if (recentlySentFlying) {
        boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
        double baseSpeedMultiplier = inLiquid ? 0.1 : (!movement.isSprinting() ? 0.3 : 0.5);
        boolean valid = movement.ticksPast(BLOCK_PLACEMENT) > 9 || !movement.onGround() || motionY >= 0.2;
        if (valid && !movement.inWeb() && (lessThanExpected || distanceMoved < baseMoveSpeed * baseSpeedMultiplier)) {
          double flyingTolerance = baseMoveSpeed * baseSpeedMultiplier;
          motionXTolerance = Math.max(motionXTolerance, flyingTolerance);
          motionZTolerance = Math.max(motionZTolerance, flyingTolerance);
          tags.add(EvaluationTag.FLYING);
        }
      }

      boolean onSlime = movement.ticksPast(SLIME_BLOCK) <= 3;
      if (onSlime && distanceMoved < 0.09) {
        motionXTolerance = Math.max(motionXTolerance, 0.02);
        motionZTolerance = Math.max(motionZTolerance, 0.02);
        tags.add(EvaluationTag.SLIME);
      }

      if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (motionY < 0 ? 0.4 : 0.2))) {
        double ladderTolerance = Math.max(distanceMoved, 0.2);
        motionXTolerance = ladderTolerance;
        motionZTolerance = ladderTolerance;
        tags.add(EvaluationTag.LADDER);
      }

      if (collidedWithEntity) {
        motionXTolerance = Math.max(motionXTolerance, 0.4);
        motionZTolerance = Math.max(motionZTolerance, 0.4);
        tags.add(EvaluationTag.COLLISION);
      }

      if (movement.physicsUnpredictableVelocityExpected()) {
        motionXTolerance = Math.max(motionXTolerance, 0.1);
        motionZTolerance = Math.max(motionZTolerance, 0.1);
//      player.sendMessage("Gave you " + (velocityDistance * 1.2 - distanceMoved) + " tolerance for velocity, plus extra " + velocityDistance + " for distance, " + distanceMoved + " for distance moved");
        tags.add(EvaluationTag.VELOCITY_FLYING);
      }

      boolean disableEdgeSneakBypass = false;

      if (movement.ticks(SNEAKING) <= 1 && movement.isSneaking() || movement.lastSneaking()) {
        double limit = 0;
        if ((abs(motionX) < 0.08 || abs(motionZ) < 0.08) || (movement.isSprinting() && protocol.cavesAndCliffsUpdate())) {
          boolean smallMovement = abs(motionX) < 0.08 && abs(motionZ) < 0.08 && movement.onGround();
          limit = movement.ticksPast(EDGE_SNEAKING) <= 1 && !disableEdgeSneakBypass ? 0.12 : (smallMovement ? 0.099 : (movement.ticksPast(EDGE_SNEAKING) < 10 && !disableEdgeSneakBypass ? flyingLimit : 0.035));
          if (!disableEdgeSneakBypass && motionY >= 0.1 && protocol.cavesAndCliffsUpdate() && movement.ticksPast(EDGE_SNEAKING) <= 1 && movement.isSprinting() && distanceMoved <= 0.5) {
            limit = 0.4;
          }
          if (abs(motionY) < 0.001) {
            limit = 0.06;
          }
          if (movement.ticksPast(EDGE_SNEAKING) <= 3 && !disableEdgeSneakBypass && !protocol.emptyFlyingPacketsAreExplicitlySent()) {
            limit = Math.max(limit, 0.065);
          }
        } else {
          if (movement.ticksPast(EDGE_SNEAKING) <= 3 || (movement.ticksPast(EDGE_SNEAKING) <= 10 && movement.onGround() && abs(motionY) < 0.01)) {
            boolean smallMovement = (abs(motionX) < 0.099 && abs(motionZ) < 0.21) || (abs(motionZ) < 0.099 && abs(motionX) < 0.21) && movement.onGround();
            if (!disableEdgeSneakBypass) {
              limit = smallMovement ? 0.2 : 0.02;
            }
          }
        }
        if (movement.inWater() || movement.inWeb()) {
          limit *= 0.25;
        }
        motionXTolerance = Math.max(motionXTolerance, limit);
        motionZTolerance = Math.max(motionZTolerance, limit);
        tags.add(EvaluationTag.SNEAKING);
      }

      double abuseX = Math.max(0, motionXDifference - motionXTolerance);
      double abuseZ = Math.max(0, motionZDifference - motionZTolerance);
      double abuseHorizontally = Hypot.fast(abuseX, abuseZ);

      MaskedMotionTolerance motionTolerance = user.meta().movement().maskedMotionTolerance;
      if (abuseHorizontally > 0.03 && protocol.maskedMotionPossible()) {
        if (motionTolerance.isMotionXWithinLimit(motionX)) {
          motionXTolerance = Math.max(motionXTolerance, 1);
//          user.sendMessage("MotionX within limit: " + motionX + " | " + motionTolerance.motionXTarget());
        }/* else {
          user.sendMessage(ChatColor.RED + "MotionX outside limit: " + motionX + " | " + motionTolerance.motionXTarget());
        }*/
        if (motionTolerance.isMotionZWithinLimit(motionZ)) {
//          user.sendMessage("MotionZ within limit: " + motionZ + " | " + motionTolerance.motionZTarget());
          motionZTolerance = Math.max(motionZTolerance, 1);
        }
        abuseX = Math.max(0, motionXDifference - motionXTolerance);
        abuseZ = Math.max(0, motionZDifference - motionZTolerance);
        abuseHorizontally = Hypot.fast(abuseX, abuseZ);
      }

//      user.sendMessage(formatDouble(motionXDifference, 2) + "/" + formatDouble(motionXTolerance, 2) + " | " + formatDouble(motionZDifference, 2) + "/" + formatDouble(motionZTolerance, 2));

      if (movement.pushedByEntity()) {
        motionXTolerance = Math.max(motionXTolerance, 0.05);
        motionZTolerance = Math.max(motionZTolerance, 0.05);
        tags.add(EvaluationTag.ENTITY_PUSH);
        abuseX = Math.max(0, motionXDifference - motionXTolerance);
        abuseZ = Math.max(0, motionZDifference - motionZTolerance);
        abuseHorizontally = Hypot.fast(abuseX, abuseZ);
      }

      boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005 && abuseHorizontally > 0.00001;

      if (inLiquid) {
//        movedTooQuickly &= distanceMoved > baseMoveSpeed;
        tags.add(EvaluationTag.WATERFLOW);
      }


      double stackMultiplier = Math.exp(-Math.min(violationLevelData.physicsInvalidMovementsInRow, 4) / 2);

      boolean movedTooQuicklyCheckable = (distanceMoved > 0.125 * stackMultiplier || violationLevelData.physicsInvalidMovementsInRow >= 8);

      boolean noCollisions = Collision.nonePresent(user, movement, BoundingBox.fromPosition(user, movement, movement.positionX(), movement.positionY(), movement.positionZ()).grow(0.1));
      if (movedTooQuickly && movedTooQuicklyCheckable && !movement.receivedFlyingPacketIn(3)) {
//      player.sendMessage("moved too quickly: " + distanceMoved + " " + predictedDistanceMoved + " " + abuseHorizontally + " " + stackMultiplier);
        if (abuseHorizontally > 0.2 * stackMultiplier) {
          horizontalViolationIncrease = 1000;
        } else if (distanceMoved - 0.1 > predictedDistanceMoved && distanceMoved > 0.3) {
          horizontalViolationIncrease = 1000;
        } else {
          int upperLimit = movement.receivedFlyingPacketIn(10) || !noCollisions ? 15 : 1000;
          horizontalViolationIncrease = Math.max(upperLimit, abuseHorizontally * 250);
        }
      } else {
        double multiplier = (abuseHorizontally > 0.1 ? 20.0 : 10.0) *
          (noCollisions ? 3 : 2) *
          (1 / stackMultiplier);

        if (abuseHorizontally < 0.1 && abs(motionX) + abs(motionZ) < 0.1 && !movement.inWeb()) {
          multiplier *= 0.1;
        }
//        user.sendMessage(abuseHorizontally + " * " + multiplier);
        horizontalViolationIncrease = abuseHorizontally * multiplier;
      }

      Timings.CHECK_PHYSICS_EVAL_HORIZONTAL.stop();
    }

    return verticalViolationIncrease + horizontalViolationIncrease;
  }

  //  private static final double RIPTIDE_TOLERANCE = 3.005;
  private static final double RIPTIDE_TOLERANCE_2 = 0.05;
  private static final double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(SimulationEnvironment movementData) {
    double riptideTolerance;
    double imminentSpinTolerance = movementData.highestLocalRiptideLevel() + 1.005;
    if (movementData.onGroundWithRiptide()) {
      riptideTolerance = movementData.ticksPast(RIPTIDE_SPIN) == 0 ? imminentSpinTolerance * 1.5 : RIPTIDE_GROUND_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.ticksPast(RIPTIDE_SPIN) == 0 ? imminentSpinTolerance : RIPTIDE_TOLERANCE_2;
    }
    return riptideTolerance;
  }
}

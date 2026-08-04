package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.check.movement.physics.evaluation.EvaluationTag;
import de.jpx3.intave.math.Hypot;
import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.entity.Player;

import java.util.Set;

import static de.jpx3.intave.check.movement.physics.MoveMetric.*;
import static java.lang.Math.abs;

public final class SimulationEvaluator {
  private static final double LADDER_UPWARDS_MOTION = (0.2 - 0.08) * 0.98005f;

  public double calculateVerticalViolationLevelIncrease(
    User user,
    double predictedY,
    boolean onLadder,
    boolean collidedWithBoat,
    Set<? super EvaluationTag> tags
  ) {
    Player player = user.player();
    MetadataBundle meta = user.meta();
    ProtocolMetadata protocol = meta.protocol();
    MovementMetadata movement = meta.movement();
    double distanceMoved = MathHelper.resolveHorizontalDistance(
      movement.positionX, movement.positionZ,
      movement.verifiedLastPositionX, movement.verifiedLastPositionZ
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
        boolean pastCollision = movement.ticksPast(NEARBY_COLLISION_INACCURACY) == 0 && !movement.inWeb;
        verticalLegitimateDeviation = pastCollision ? 0.15 : (0.08);
        tags.add(EvaluationTag.FLYING);
        if (pastCollision) {
          tags.add(EvaluationTag.COLLISION_INACCURACY);
        }
      }
    }

    if (pose.height(user) < 1 && receivedMotionY <= 0 && accountedSkippedMovement) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
      tags.add(EvaluationTag.CROUCHING_BOX_INACCURACY);
    }

    // MotionY calculations with sin/cos (FastMath affected)
    boolean fastMathAffected = pose == Pose.SWIMMING || pose == Pose.FALL_FLYING;
    if (fastMathAffected) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.001);
    }

    if ((movement.ticksPast(WATERFLOW_PUSH) < 10 || movement.inLava()) && distanceMoved < 0.2) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.02);
      tags.add(EvaluationTag.WATERFLOW);
    }

    // Riptide
    if (movement.ticksPast(RIPTIDE_SPIN) < 4) {
      verticalLegitimateDeviation = resolveRiptideDeviation(movement);
      tags.add(EvaluationTag.RIPTIDE);
    }

    // Firework
    if (movement.ticksPast(FIREWORK_ROCKETS) < 10 * movement.fireworkRocketsPower) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
      tags.add(EvaluationTag.FIREWORK);
    } else if (movement.ticksPast(FIREWORK_ROCKETS) < 30 * movement.fireworkRocketsPower) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.75);
      tags.add(EvaluationTag.FIREWORK);
    }

    if (movement.shulkerYToleranceRemaining > 0 && // tick restriction
      (movement.positionY >= movement.lowestShulkerY - 1 && movement.positionY <= movement.highestShulkerY + 1) && // height restriction
      receivedMotionY - movement.jumpMotion() < 0.2 && // motion restriction
      (abs(receivedMotionY) <= 0.5 || ((movement.positionY % 0.05) < 0.0001 && (abs(receivedMotionY - movement.jumpMotion()) < 0.01 || (receivedMotionY <= 0 && receivedMotionY > -.8)))) // various other restrictions
    ) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
      tags.add(EvaluationTag.SHULKER);
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      // Check if the player box is inside the piston box
      if (movement.pistonCollisionArea != null && movement.pistonCollisionArea.intersectsWith(movement.boundingBox())) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, movement.pistonVerticalAllowance);
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
        tags.add(EvaluationTag.CROUCHING_BOX_INACCURACY);
      }
    }

    if (movement.receivedFlyingPacketIn(3) && differenceY > 0.001 && protocol.combatUpdate() && movement.ticksPast(BLOCK_PLACEMENT) > 10) {
      boolean inLiquid = movement.ticksPast(IN_WATER) <= 10 || movement.inLava();
      int allowedPackets = Hypot.fast(movement.motionX(), movement.motionZ()) < 0.03 ? 3 : 1;
      if (inLiquid || movement.physicsPacketRelinkFlyVL++ <= allowedPackets) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, inLiquid ? 0.1 : 0.03);
        tags.add(EvaluationTag.FLYING);
      }
    }

    if (movement.ticksPast(BLOCK_PLACEMENT) < 10 && movement.receivedFlyingPacketIn(3)) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.03);
//      player.sendMessage(movement.receivedFlyingPacketIn(3) + " " + differenceY + " " + movement.past(BLOCK_PLACEMENT));
      tags.add(EvaluationTag.FLYING);
    }

    if (movement.physicsUnpredictableVelocityExpected) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
      tags.add(EvaluationTag.VELOCITY_FLYING_INACCURACY);
    }

    if (collidedWithBoat && !movement.isInVehicle() && movement.motionY() < 0.605) {
      if (movement.enforceBoatStep) {
        if (movement.motionY() < 0.1) {
          verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
          tags.add(EvaluationTag.BOAT);
        }
        movement.enforceBoatStep = false;
      } else if (movement.baseMotionY < 0) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 1);
        if (movement.motionY() > movement.jumpMotion()) {
          movement.enforceBoatStep = true;
        }
        tags.add(EvaluationTag.BOAT);
      }
    }

    boolean criticalWeb = receivedMotionY > -0.01
        && movement.ticksPast(IN_WEB) < 10
        && !movement.inWater
        && !movement.inLava()
        && movement.positionY % 1 > 0.1
        && movement.ticksPast(EXTERNAL_VELOCITY) != 0;

    boolean movingUpwardsInWeb = movement.ticks(IN_WEB) > 2 && movement.motionY() >= 0 && !movement.onGround && movement.ticksPast(EXTERNAL_VELOCITY) > 3;

    if (movement.inWeb) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, movingUpwardsInWeb ? 0.00001 :/*criticalWeb ? 0.000001 : */0.13);
      tags.add(EvaluationTag.WEB);
    }

    if (movement.ticksPast(IN_WEB) < 10 && !movement.inWeb && differenceY < 0.1) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
      tags.add(EvaluationTag.WEB);
    }

    if (movement.receivedFlyingPacketIn(1) && movement.ticksPast(EXTERNAL_VELOCITY) <= 4) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.03);
      tags.add(EvaluationTag.FLYING);
      tags.add(EvaluationTag.VELOCITY_FLYING_INACCURACY);
    }

    if (movement.receivedFlyingPacketIn(2) && (movement.inWater() || movement.inLava())) {
      if (Math.abs(movement.motionY()) < 0.1 &&
        Math.abs(movement.motionX()) < 0.1 &&
        Math.abs(movement.motionZ()) < 0.1 &&
        Math.abs(predictedY) < 0.1 &&
        movement.ticksPast(EXTERNAL_VELOCITY) > 8
      ) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.1);
        tags.add(EvaluationTag.FLYING);
      }
    }

    if (movement.inWater && movement.ticks(IN_WATER) < 2) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.05);
      tags.add(EvaluationTag.WATERFLOW);
    }

    if (movement.ticksPast(VEHICLE_ATTACHMENT) <= 1 && movement.isInVehicle()) {
      Entity vehicle = movement.vehicle();
      BoundingBox grownBoatBox = vehicle.boundingBox().grow(0.5);
      BoundingBox nextPlayerBox = BoundingBox.fromPosition(
        user, movement, movement.positionX, movement.positionY, movement.positionZ
      );
      BoundingBox currentPlayerBox = movement.boundingBox();
      boolean attachAllowed = grownBoatBox.intersectsWith(nextPlayerBox) || grownBoatBox.intersectsWith(currentPlayerBox);
      double moveToAttachMoveDelta = abs(movement.motion().length() - distanceMoved);
      boolean movementInLineWithDistance = moveToAttachMoveDelta < 0.5;
//      player.sendMessage(attachAllowed + " " + movementInLineWithDistance + " " + moveToAttachMoveDelta);
      if (attachAllowed /*&& movementInLineWithDistance*/) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 5);
        tags.add(EvaluationTag.ATTACH);
      }
    }

    if (movement.ticksPast(VEHICLE_DETACHMENT) <= 2 && Math.abs(movement.motionY()) < 0.1) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.3);
    }

    // Jump out of water
    if (movement.ticksPast(IN_WATER) <= 3 || movement.ticksPast(IN_LAVA) <= 3) {
      double liquidMotionY;
      if (protocol.waterUpdate()) {
        liquidMotionY = receivedMotionY + 0.6f - movement.positionY + movement.verifiedLastPositionY;
      } else {
        liquidMotionY = receivedMotionY + 0.3f;
      }
      boolean offsetPositionInLiquid = MovementCharacteristics.isOffsetPositionInLiquid(
        user, movement.boundingBox(), receivedMotionX, liquidMotionY, receivedMotionZ
      );
      boolean maybeCollidedHorizontally = Collision.nearSolidBlock(user, movement.boundingBox().grow(0.2, 0.5, 0.2));
      boolean targetMotion = Math.abs(receivedMotionY - 0.3) < 0.001 || Math.abs(receivedMotionY - 0.34) < 0.001 || Math.abs(receivedMotionY - 0.2470) < 0.001;
      if (maybeCollidedHorizontally && offsetPositionInLiquid && targetMotion) {
        verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.7f);
        tags.add(EvaluationTag.WATERFLOW);
      }
    }

    // Bubble column ejection desync
    if (movement.ticksPast(IN_WATER) <= 10 && Math.max(receivedMotionY, predictedY) > 0.35) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.8);
      tags.add(EvaluationTag.WATERFLOW);
    }

    // Sometimes shit happens
    if (movement.ticks(SNEAKING) <= 1 && !movement.inWater && !movement.inWeb && (movement.onGround() || movement.lastOnGround()) && movement.motionY() <= 0 && movement.motionY() >= -0.5 && movement.lastSneaking) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.08f);
      tags.add(EvaluationTag.SNEAKING);
    }

    // Knockback timing tolerance -- see the horizontal counterpart in
    // calculateHorizontalViolationIncrease. Bounded by the velocity actually sent.
    if (movement.velocityToleranceTicks > 0) {
      verticalLegitimateDeviation = Math.max(
        verticalLegitimateDeviation,
        Math.min(0.45, movement.velocityToleranceMagnitude * 0.9)
      );
      tags.add(EvaluationTag.KNOCKBACK);
    }

    // Entity pushes are horizontal, but being shoved changes which blocks the player
    // collides with, so steps and ground contact land a tick apart from ours.
    if (movement.pushedByEntity || movement.entityPushToleranceTicks > 0) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.06);
      tags.add(EvaluationTag.ENTITY_PUSH);
    }

    // Inside a block: see the horizontal counterpart. Vertically this is the worse
    // half -- we resolve ground contact against a box the player is inside, so we
    // disagree about whether they are standing, falling or being lifted.
    if (movement.insideBlockToleranceTicks > 0) {
      verticalLegitimateDeviation = Math.max(verticalLegitimateDeviation, 0.2);
      tags.add(EvaluationTag.COLLISION_INACCURACY);
    }

    // A launch off the ground that is WEAKER than the one we predicted (block jump
    // factor, jump boost, a jump-strength attribute the server resolved differently)
    // cannot be an advantage, so don't punish the difference. Only upward motion
    // qualifies -- falling faster than predicted stays a violation.
    // (receivedMotionY >= 0 on purpose: "we predicted a step or a jump the client did
    // not take" is the same situation seen from the other side, and staying put is
    // never an advantage)
    if ((movement.lastOnGround() || movement.onGround())
      && receivedMotionY >= 0 && predictedY > 0.1 && predictedY > receivedMotionY
    ) {
      verticalLegitimateDeviation = Math.max(
        verticalLegitimateDeviation,
        Math.min(0.45, predictedY - receivedMotionY)
      );
      tags.add(EvaluationTag.WEAK_JUMP);
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
      if (!movement.inWater && movement.ticksPast(IN_WATER) <= 2 && abs(receivedMotionY) < 0.1) {
        multiplier *= 0.01;
      } else if (movement.motionY() >= 0 && movement.onGround) {
        multiplier *= 0.1;
      } else {
        multiplier *= 0.25;
      }
      tags.add(EvaluationTag.ELYTRA);
    } else if (movement.ticksPast(ELYTRA_FLYING) < 4 && movement.motionY() < movement.jumpMotion()) {
      multiplier *= 0.1;
      tags.add(EvaluationTag.ELYTRA);
    }

    if (criticalWeb) {
//      multiplier *= 40;
    }

    boolean justInPowderSnow = movement.ticksPast(IN_POWDER_SNOW) < 5;
    // The exact climb speed is not one constant: it differs per climbable (ladder,
    // vine, scaffolding, powder snow), between client versions, and again when the
    // player climbs while in water or lets go and slides. Pinning it to
    // LADDER_UPWARDS_MOTION flagged ordinary climbing on 1.21.x. Accept the whole
    // plausible climbing band instead -- being on a climbable is not an advantage,
    // the player still cannot leave it, and horizontal speed is judged separately.
    if (onLadder || justInPowderSnow) {
      if (movement.motionY() <= 0.4 && movement.motionY() >= -0.5) {
        abuseVertically = 0;
        tags.add(EvaluationTag.LADDER);
      }
    }

    // Bubble columns (soul sand up / magma down). Whether we reproduce the column at
    // all depends on resolving the block AND the water above it AND the "drag"
    // property on this server version; when any of that is off, the whole column
    // impulse shows up as a vertical difference every tick of the ride. Being in one
    // is not an advantage -- you cannot steer it and it only exists where the server
    // itself placed the blocks.
    if (movement.bubbleColumnToleranceTicks > 0) {
      abuseVertically = 0;
      tags.add(EvaluationTag.WATERFLOW);
    }

    // Auto-step. Walking into a slab/stair/bottom-trapdoor edge lifts the client by up
    // to its step height inside a single tick. If we did not reproduce that step -- a
    // block shape we resolve differently, or the collision landing a tick apart -- the
    // entire step shows up as vertical abuse. Requiring ground contact before AND
    // after keeps this from being usable as a lift: there has to be a real block.
    if (receivedMotionY > 0 && receivedMotionY <= 0.6
      && movement.lastOnGround()
      && Hypot.fast(receivedMotionX, receivedMotionZ) > 0.02
      && predictedY < receivedMotionY
    ) {
      abuseVertically = 0;
      tags.add(EvaluationTag.STEP);
    }

    // Long teleport
    if (movement.ticksPast(LONG_TELEPORT) <= 10 && movement.motionY() < -0.097 && movement.motionY() > -0.099) {
      double horizontalDistance = Hypot.fast(receivedMotionX, receivedMotionZ);
      if (horizontalDistance < 0.2) {
        abuseVertically = 0;
        tags.add(EvaluationTag.CHUNK_LOADING);
      }
    }
    return abuseVertically * multiplier;
  }

  public double calculateHorizontalViolationIncrease(
    User user,
    double predictedX, double predictedZ,
    boolean onLadder, boolean collidedWithBoat,
    Set<? super EvaluationTag> tags
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
      movement.verifiedLastPositionX, movement.verifiedLastPositionZ
    );
    double predictedDistanceMoved = Hypot.fast(predictedX, predictedZ);

    if (movement.simulator() == Simulators.HORSE) {
      if (distanceMoved < predictedDistanceMoved) {
        return 0;
      }
    }

    double distance = MathHelper.resolveHorizontalDistance(predictedX, predictedZ, motionX, motionZ);
    boolean pushedByWaterFlow = movement.ticksPast(WATERFLOW_PUSH) <= 20;
    double horizontalLegitimateDeviation;
    if (movement.ticksPast(ATTACK_REDUCE) <= 1) {
//      horizontalLegitimateDeviation = 0.005;
      if (movement.receivedFlyingPacketIn(4)) {
        horizontalLegitimateDeviation = 0.03;
      } else {
        horizontalLegitimateDeviation = 0.015;
      }
      tags.add(EvaluationTag.REDUCE_INACCURACY);
    } else {
      horizontalLegitimateDeviation = 0.0007;
      if (distance > 0.0007) {
        boolean collides = Collision.nearSolidBlock(user, movement.boundingBox().growHorizontally(0.001)) && !movement.inWeb;
        if (collides) {
          horizontalLegitimateDeviation = distanceMoved < 0.04 ? 0.04 : 0.003;
          tags.add(EvaluationTag.COLLISION_INACCURACY);
        }
      }
      if (user.meta().protocol().beeUpdate() && (abs(motionX) < 0.09 || abs(motionZ) < 0.09)) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.009);
        tags.add(EvaluationTag.FLYING);
      }
    }

    if ((movement.shulkerXToleranceRemaining > 0 || movement.shulkerZToleranceRemaining > 0) && abs(motionY) < 0.5 && abs(motionZ) < 0.5) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.3);
      tags.add(EvaluationTag.SHULKER);
    }

    if (movement.shulkerYToleranceRemaining > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, abs(movement.motionY()) < .3 ? .3 : .1);
      tags.add(EvaluationTag.SHULKER);
    }
    if (movement.collidedHorizontally) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.027);
      tags.add(EvaluationTag.COLLISION_INACCURACY);
    }

    if (movement.pistonMotionToleranceRemaining > 0) {
      // Check if the player box is inside the piston box
      if (movement.pistonCollisionArea != null && movement.pistonCollisionArea.intersectsWith(movement.boundingBox())) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, movement.pistonHorizontalAllowance);
        tags.add(EvaluationTag.PISTON);
      }
    }

    if (pushedByWaterFlow) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.018);
      tags.add(EvaluationTag.WATERFLOW);
    }

    if (movement.currentlyInBlock && !movement.inWeb && predictedDistanceMoved < distanceMoved * 1.3) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, predictedDistanceMoved);
      tags.add(EvaluationTag.COLLISION_INACCURACY);
    }

    // Firework
    if (movement.ticksPast(FIREWORK_ROCKETS) < 30 * movement.fireworkRocketsPower) {
      // srsly who cares
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 3);
      tags.add(EvaluationTag.FIREWORK);
    }

    // Flying packet
    double flyingLimit = 0.05;
    if (movement.receivedFlyingPacketIn(2)) {
      if (movement.onGround) {
        boolean specialWeb = movement.inWeb;
        boolean lessThanExpected = distanceMoved < 0.15;
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, specialWeb ? 0.1 : (lessThanExpected ? 0.115 : flyingLimit));
        tags.add(EvaluationTag.FLYING_ON_GROUND);
      } else {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, flyingLimit);
        tags.add(EvaluationTag.FLYING);
      }
      if (movement.ticksPast(NEARBY_COLLISION_INACCURACY) == 0) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, flyingLimit);
        tags.add(EvaluationTag.FLYING);
        tags.add(EvaluationTag.COLLISION_INACCURACY);
      }
    }

    // Riptide
    if (movement.ticksPast(RIPTIDE_SPIN) < 4) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, resolveRiptideDeviation(movement));
//      player.sendMessage(Collision.present(player, BoundingBox.fromPosition(user, movement, movement.positionX, movement.positionY, movement.positionZ).grow(0.1)) + " " + movement.past(RIPTIDE_SPIN));
      tags.add(EvaluationTag.RIPTIDE);
    }

    if (movement.ticksPast(VEHICLE_ATTACHMENT) <= 1 && movement.isInVehicle()) {
      Entity vehicle = movement.vehicle();
      BoundingBox grownBoatBox = vehicle.boundingBox().grow(0.5);
      BoundingBox nextPlayerBox = BoundingBox.fromPosition(
        user, movement, movement.positionX, movement.positionY, movement.positionZ
      );
      BoundingBox currentPlayerBox = movement.boundingBox();
      boolean attachAllowed = grownBoatBox.intersectsWith(nextPlayerBox) || grownBoatBox.intersectsWith(currentPlayerBox);
//      double moveToAttachMoveDelta = abs(movement.motion().length() - distanceMoved);
//      boolean movementInLineWithDistance = moveToAttachMoveDelta < 0.5;
      if (attachAllowed /*&& movementInLineWithDistance*/) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 5);
        tags.add(EvaluationTag.ATTACH);
        if (distance > 0.1) {
          movement.physicsResetMotionX = true;
          movement.physicsResetMotionZ = true;
        }
      }
    }

    boolean recentlySentFlying = movement.receivedFlyingPacketIn(2);
    double baseMoveSpeed = movement.baseMoveSpeed();
    boolean inLiquid = (movement.ticksPast(IN_WATER) < 20 && movement.ticksPast(WATERFLOW_PUSH) > 5) || movement.inLava();

    if (recentlySentFlying) {
      boolean lessThanExpected = distanceMoved <= predictedDistanceMoved;
      double baseSpeedMultiplier = inLiquid ? 0.1 : (!movement.sprinting ? 0.3 : 0.5);
      boolean valid = movement.ticksPast(BLOCK_PLACEMENT) > 9 || !movement.onGround() || motionY >= 0.2;
      if (valid && !movement.inWeb && (lessThanExpected || distanceMoved < baseMoveSpeed * baseSpeedMultiplier)) {
        horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, baseMoveSpeed * baseSpeedMultiplier);
        tags.add(EvaluationTag.FLYING);
      }
    }

    if (onLadder && (distanceMoved < predictedDistanceMoved || distanceMoved < (movement.motionY() < 0 ? 0.4 : 0.2))) {
      horizontalLegitimateDeviation = Math.max(distanceMoved, 0.2);
      tags.add(EvaluationTag.LADDER);
    }

    if (collidedWithBoat) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.4);
      tags.add(EvaluationTag.BOAT);
    }

    if (movement.physicsUnpredictableVelocityExpected) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.1);
//      player.sendMessage("Gave you " + (velocityDistance * 1.2 - distanceMoved) + " tolerance for velocity, plus extra " + velocityDistance + " for distance, " + distanceMoved + " for distance moved");
      tags.add(EvaluationTag.VELOCITY_FLYING_INACCURACY);
    }

    if (movement.ticks(SNEAKING) <= 1 && movement.sneaking || movement.lastSneaking) {
      double limit = 0;
      if ((abs(movement.motionX()) < 0.08 || abs(movement.motionZ()) < 0.08) || (movement.sprinting && protocol.cavesAndCliffsUpdate())) {
        boolean smallMovement = abs(movement.motionX()) < 0.08 && abs(movement.motionZ()) < 0.08 && movement.onGround();
        limit = movement.ticksPast(EDGE_SNEAKING) <= 1 ? 0.12 : (smallMovement ? 0.099 : (movement.ticksPast(EDGE_SNEAKING) < 10 ? flyingLimit : 0.035));
        if (movement.motionY() >= 0.1 && protocol.cavesAndCliffsUpdate() && movement.ticksPast(EDGE_SNEAKING) <= 1 && movement.sprinting && distanceMoved <= 0.5) {
          limit = 0.4;
        }
        if (abs(movement.motionY()) < 0.001) {
          limit = 0.06;
        }
        if (movement.ticksPast(EDGE_SNEAKING) <= 3 && !protocol.flyingPacketsAreSent()) {
          limit = Math.max(limit, 0.065);
        }
      } else {
        if (movement.ticksPast(EDGE_SNEAKING) <= 3 || (movement.ticksPast(EDGE_SNEAKING) <= 10 && movement.onGround() && abs(motionY) < 0.01)) {
          boolean smallMovement = (abs(movement.motionX()) < 0.099 && abs(movement.motionZ()) < 0.21) || (abs(movement.motionZ()) < 0.099 && abs(movement.motionX()) < 0.21) && movement.onGround();
          limit = smallMovement ? 0.2 : 0.02;
        }
      }
      if (movement.inWater || movement.inWeb) {
        limit *= 0.25;
      }
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, limit);
      tags.add(EvaluationTag.SNEAKING);
    }

    // Entity-collision push tolerance. Keyed off a decaying proximity window
    // (armed in Physics#nearPushableEntity), not just the exact tick our simulator
    // reproduced a push: the client applies push against its own network-desynced
    // view of entity positions, so a fixed single-tick flag misses the pushes that
    // caused false flags in high-ping combat. Tolerance is 0.08 because a diagonal
    // push reaches ~0.05/axis (~0.07 combined) plus prediction slop.
    boolean entityPushActive = movement.pushedByEntity || movement.entityPushToleranceTicks > 0;
    if (entityPushActive) {
      // Up to 0.05 per axis (~0.07 diagonally) each side, and the client may push off
      // an entity we did not predict a push against while we predicted one it did not
      // -- so the two can disagree by more than a single push.
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.12);
      tags.add(EvaluationTag.ENTITY_PUSH);
    }

    // Knockback timing tolerance. The client applies a server velocity on the tick the
    // packet reaches it; we apply it when the transaction anchoring that packet is
    // acknowledged. Under latency (and, on region-threaded servers, packet reordering)
    // those are different ticks, and for that handful of ticks the received motion
    // legitimately differs from the prediction by up to the knockback itself. Bounded
    // by the velocity that was actually sent, so this can never grant more speed than
    // the server itself handed out.
    boolean knockbackActive = movement.velocityToleranceTicks > 0;
    if (knockbackActive) {
      horizontalLegitimateDeviation = Math.max(
        horizontalLegitimateDeviation,
        Math.min(0.5, movement.velocityToleranceMagnitude * 0.8)
      );
      tags.add(EvaluationTag.KNOCKBACK);
    }

    // The prone ("moving slowly") state is resolved one tick apart from the client's,
    // see PredictiveSimulationProcessor#resolveProneAmbiguity. The retry there usually
    // finds the right assumption; this covers the ticks where neither variant fits
    // because the transition happened mid-tick.
    // Auto-step, horizontal half: when the client walks up a slab/stair/bottom
    // trapdoor and we do not reproduce the step, our collider stops the player dead at
    // the block edge while the client keeps its full speed over it -- so the same tick
    // that mispredicts the height also mispredicts the whole horizontal move. Gated on
    // a step-shaped upward move from the ground that ends on the ground or against a
    // wall, which a jump into open air does not match.
    boolean steppedUnexpectedly = motionY > 0 && motionY <= 0.6
      && movement.lastOnGround() && (movement.onGround() || movement.collidedHorizontally)
      && distanceMoved > predictedDistanceMoved;
    if (steppedUnexpectedly) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.25);
      tags.add(EvaluationTag.STEP);
    }

    // Bubble column: the ride perturbs the horizontal too (the column re-centres the
    // player), and on the way out the client is briefly in air physics while we still
    // think it is in water.
    if (movement.bubbleColumnToleranceTicks > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.15);
      tags.add(EvaluationTag.WATERFLOW);
    }

    // Inside a block (a trapdoor closed on the player, a block placed into them): our
    // collider resolves against a box they are already in, the client's does not push
    // them out at all, so the two disagree on every axis.
    if (movement.insideBlockToleranceTicks > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.15);
      tags.add(EvaluationTag.COLLISION_INACCURACY);
    }

    // because the retry keeps whichever variant is closer, the leftover can only be
    // about half the difference between crawling and walking acceleration
    if (movement.proneAmbiguityTicks > 0) {
      horizontalLegitimateDeviation = Math.max(horizontalLegitimateDeviation, 0.045);
      tags.add(EvaluationTag.DESYNC);
    }

    double abuseHorizontally = Math.max(0, distance - horizontalLegitimateDeviation);
    boolean movedTooQuickly = distanceMoved > predictedDistanceMoved * 1.0005 && abuseHorizontally > 0;

    if (inLiquid) {
      movedTooQuickly &= distanceMoved > baseMoveSpeed;
      tags.add(EvaluationTag.WATERFLOW);
    }

    // A+D spam
//    if (movement.past(FLYING_PACKET_ACCURATE) < 1 && predictedDistanceMoved < 0.15 && distanceMoved < 0.15 && abuseHorizontally < 0.15 && Math.abs(motionY) < 0.01) {
//      movedTooQuickly = false;
//      abuseHorizontally = 0;
//      Bukkit.broadcastMessage(predictedDistanceMoved + " " + distanceMoved + " " + abuseHorizontally);
//    }

    Pose pose = movement.pose();
    boolean flewWithElytra = movement.ticksPast(ELYTRA_FLYING) <= 3;

    if (pose == Pose.FALL_FLYING) {
      if (!movement.inWater && movement.ticksPast(IN_WATER) <= 2 && distance < 0.3) {
        abuseHorizontally *= 0.2;
      } else if (movement.motionY() >= 0 && movement.onGround) {
        abuseHorizontally *= 0.3;
      } else {
        abuseHorizontally *= 0.6;
      }
      tags.add(EvaluationTag.ELYTRA);
    } else if (flewWithElytra) {
      abuseHorizontally *= 0.1;
      tags.add(EvaluationTag.ELYTRA);
    }

    double stackMultiplier = Math.exp(-Math.min(violationLevelData.physicsInvalidMovementsInRow, 4) / 2);
    // Repeated violations are supposed to tighten these thresholds, but the raw
    // multiplier bottoms out at 0.135 and then a THREE CENTIMETRE residual returns
    // 1000 -- which is how one desynced tick escalated into a permanent storm. The
    // floor keeps the escalation (still ~3x tighter than baseline) without letting
    // rounding-sized differences count as "moved way too quickly".
    double thresholdScale = Math.max(stackMultiplier, 0.35);

    boolean movedTooQuicklyCheckable = (distanceMoved > 0.125 * thresholdScale || violationLevelData.physicsInvalidMovementsInRow >= 8)
      && !flewWithElytra;

    // The prone window is a pose transition (standing up out of a crawl, a trapdoor
    // toggling above the player): for those few ticks the client and we disagree about
    // whether the input was scaled, which is a centimetres-sized difference -- not a
    // reason to take the branch that answers with 1000.
    if (movedTooQuickly && movedTooQuicklyCheckable && !movement.physicsUnpredictableVelocityExpected
      && !entityPushActive && !knockbackActive && !steppedUnexpectedly
      && movement.proneAmbiguityTicks <= 0
      && movement.insideBlockToleranceTicks <= 0
      && movement.bubbleColumnToleranceTicks <= 0) {
//      player.sendMessage("moved too quickly: " + distanceMoved + " " + predictedDistanceMoved + " " + abuseHorizontally + " " + stackMultiplier);
      if (abuseHorizontally > 0.2 * thresholdScale) {
        return 1000;
      }
      if (distanceMoved - 0.1 > predictedDistanceMoved && distanceMoved > 0.3) {
        return 1000;
      }
      return Math.max(15, abuseHorizontally * 250);
    }
    boolean noCollisions = Collision.nonePresent(user, movement, BoundingBox.fromPosition(user, movement, movement.positionX, movement.positionY, movement.positionZ).grow(0.1));
    // The stack multiplier is a positive feedback loop: flagged ticks raise
    // physicsInvalidMovementsInRow, which raises the multiplier, which raises the
    // violation level, which keeps the player desynced and flagging. Left uncapped it
    // reached ~7.4x and turned centimetre residuals into +1000 detections. Capped, a
    // repeated real violation still escalates, just not exponentially.
    double stackAmplifier = Math.min(1 / stackMultiplier, 3.0);
    double multiplier = (abuseHorizontally > 0.1 ? 20.0 : 10.0) *
      (noCollisions ? 3 : 2) *
      stackAmplifier;
    if (knockbackActive) {
      multiplier = Math.min(multiplier, 10.0);
    }
    if (entityPushActive) {
      // Never let ping-desynced collision residue feed the exponential stack
      // multiplier (1/stackMultiplier) -- that positive-feedback loop is what turned
      // sub-0.05 residuals into the 15 -> 300 -> 20000 escalation. Any residue beyond
      // the 0.08 tolerance stays linear and small while entities are in contact.
      multiplier = Math.min(multiplier, 10.0);
    }
//    player.sendMessage("abuseHorizontally: " + abuseHorizontally + " multiplier: " + multiplier);
    if (abuseHorizontally < 0.1 && Math.abs(motionX) + Math.abs(motionZ) < 0.1 && !inLiquid && !movement.inWeb) {
      multiplier *= 0.1;
    }
    return abuseHorizontally * multiplier;
  }

  //  private static final double RIPTIDE_TOLERANCE = 3.005;
  private static final double RIPTIDE_TOLERANCE_2 = 0.05;
  private static final double RIPTIDE_GROUND_TOLERANCE_2 = 2.5;

  private double resolveRiptideDeviation(MovementMetadata movementData) {
    double riptideTolerance;
    double imminentSpinTolerance = movementData.highestLocalRiptideLevel + 1.005;
    if (movementData.onGroundWithRiptide) {
      riptideTolerance = movementData.ticksPast(RIPTIDE_SPIN) == 0 ? imminentSpinTolerance * 1.5 : RIPTIDE_GROUND_TOLERANCE_2;
    } else {
      riptideTolerance = movementData.ticksPast(RIPTIDE_SPIN) == 0 ? imminentSpinTolerance : RIPTIDE_TOLERANCE_2;
    }
    return riptideTolerance;
  }
}
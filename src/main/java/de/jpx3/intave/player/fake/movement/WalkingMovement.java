package de.jpx3.intave.player.fake.movement;

import de.jpx3.intave.math.Hypot;
import org.bukkit.Location;

/**
 * This movement mode performs entity movement with real gravity, means that the bot can fall down.
 */
public final class WalkingMovement extends Movement {
  private final static double JUMP_UPWARDS_MOTION = 0.42f;
  private final static double MOVE_MULTIPLIER = 0.91f;
  private final static double MIN_MOVEMENT_DIST = 0.005;
  private int lastJump = 0;

  @Override
  public void move(Location parentLocation) {
    Location expectedLocation = PositionRotationLookup.lookup(parentLocation, this.botDistance);
    if (this.velocityChanged) {
      expectedLocation.add(this.velocityX, this.velocityY, this.velocityZ);
    } else {
      expectedLocation.add(this.velocityX, 0.0, this.velocityZ);
    }
    double distance = safeDistance(location, expectedLocation);//this.location.distance(expectedLocation);
    if (this.prevLocation != null) {
      calculateMovement(expectedLocation, distance);
    }
    this.velocityX *= MOVE_MULTIPLIER;
    this.velocityZ *= MOVE_MULTIPLIER;
  }

  @Override
  public void combatEvent() {
    this.botDistance = 3.0;
  }

  @Override
  public double minBotDistance() {
    return 2.5;
  }

  private void calculateMovement(Location expectedLocation, double distance) {
    if (moveOnTopOfPlayer()) {
      expectedLocation.add(0.0, 2.1, 0.0);
    }
    limitSmallMovement();
    double startMotionX = this.motionX;
    double startMotionZ = this.motionZ;
    this.motionX = (expectedLocation.getX() - this.location.getX()) * MOVE_MULTIPLIER;
    this.motionZ = (expectedLocation.getZ() - this.location.getZ()) * MOVE_MULTIPLIER;
    if (lastOnGround == 1) {
      this.motionX *= 0.6;
      this.motionZ *= 0.6;
    }
    boolean movedEnoughForJump = Hypot.fast(startMotionX - motionX, startMotionZ - motionZ) > 0.1;
    if (moveOnTopOfPlayer()) {
      this.motionY = (expectedLocation.getY() - this.location.getY());
    } else {
      if (this.onGround && ((movedEnoughForJump && ++this.lastJump > 2 && distance > 1.0) || collidedHorizontally)) {
        this.lastJump = 0;
        jump();
      } else if (!this.onGround) {
        this.motionY -= 0.08;
        this.motionY *= 0.98f;
      }
    }
    if (this.sneaking) {
      this.motionX *= 0.2;
      this.motionZ *= 0.2;
    }
    if (!moveOnTopOfPlayer()) {
      tryJumpBoost(expectedLocation);
    }
  }

  @Override
  public double maxBotDistance() {
    return 17;
  }

  private void tryJumpBoost(Location expectedLocation) {
    double deviation = expectedLocation.getY() - location.getY();
    if (deviation > 7) {
      jump();
      motionY *= 3.5;
    } else if (deviation > 4) {
      jump();
      motionY *= 2.0;
    } else if (deviation > 1.5 && onGround) {
      jump();
    }
  }

  private void jump() {
    float f = rotationYaw * 0.017453292F;
    this.motionX -= Math.sin(f) * 0.2f;
    this.motionY = JUMP_UPWARDS_MOTION;
    this.motionZ += Math.cos(f) * 0.2f;
  }

  private void limitSmallMovement() {
    if (Math.abs(this.motionX) < MIN_MOVEMENT_DIST) {
      this.motionX = 0.0;
    }
    if (Math.abs(this.motionY) < MIN_MOVEMENT_DIST) {
      this.motionY = 0.0;
    }
    if (Math.abs(this.motionZ) < MIN_MOVEMENT_DIST) {
      this.motionZ = 0.0;
    }
  }
}
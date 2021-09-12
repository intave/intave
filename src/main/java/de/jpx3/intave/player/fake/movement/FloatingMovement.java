package de.jpx3.intave.player.fake.movement;

import org.bukkit.Location;

/**
 * This movement mode doesn't apply real gravity on the entity.
 */
public final class FloatingMovement extends Movement {
  private final static double MOVE_MULTIPLIER = 0.91f;

  @Override
  public void move(Location parentLocation) {
    Location expectedLocation = PositionRotationLookup.lookup(parentLocation, this.botDistance);
    if (moveOnTopOfPlayer()) {
      expectedLocation.add(0.0, 2.1, 0.0);
    }
    if (this.velocityChanged) {
      expectedLocation.add(this.velocityX, this.velocityY, this.velocityZ);
    } else {
      expectedLocation.add(this.velocityX, 0.0, this.velocityZ);
    }
    this.motionX = expectedLocation.getX() - this.location.getX();
    this.motionY = expectedLocation.getY() - this.location.getY();
    this.motionZ = expectedLocation.getZ() - this.location.getZ();
    this.velocityX *= MOVE_MULTIPLIER;
    this.velocityZ *= MOVE_MULTIPLIER;
  }

  @Override
  public double minBotDistance() {
    return 4.0;
  }

  @Override
  public boolean doBlockCollisions() {
    return false;
  }
}
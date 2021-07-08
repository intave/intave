package de.jpx3.intave.fakeplayer.movement.types;

import de.jpx3.intave.fakeplayer.movement.HeadRotationMovement;
import de.jpx3.intave.fakeplayer.movement.LocationUtils;
import de.jpx3.intave.tools.AccessHelper;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

public abstract class Movement extends HeadRotationMovement {
  private final static double BOT_DISTANCE_ADJUSTMENT = 0.15;

  public double motionX = 0.0, motionY = 0.0, motionZ = 0.0;
  public Location location;
  public Location prevLocation;
  public boolean onGround = false;
  public boolean collidedHorizontally;
  public int lastOnGround = 0;
  public boolean sprinting = false, sneaking = false;
  public double velocityX = 0.0, velocityY = 0.0, velocityZ = 0.0;
  public boolean velocityChanged = false;
  public double botDistance = 0.0;
  private Location prevParentLocation;
  private int lastCombatEvent = 100;
  public long moveOnTopOfPlayerTime;

  Movement() {
  }

  protected abstract void move(Location parentLocation);

  public void combatEvent() {
    this.botDistance = 2.0;
    this.lastCombatEvent = 0;
  }

  public boolean doBlockCollisions() {
    return true;
  }

  public final void applyMovementAndRotation(
    Location parentLocation
  ) {
    if (shouldMove(parentLocation) && this.lastCombatEvent++ > 50) {
      updateBotDistance();
    }

    if (this.onGround) {
      this.lastOnGround = 0;
    } else {
      this.lastOnGround++;
    }
    move(parentLocation);
    double startMotionX = this.motionX;
    double startMotionZ = this.motionZ;
//    QuickColliderSimulationResult result = Collider.simulateQuickCollision(
//      location.getWorld(),
//      location.getX(), location.getY(), location.getZ(),
//      motionX, motionY, motionZ
//    );
//    if (doBlockCollisions()) {
////      this.motionX = result.motionX();
//      this.motionY = result.motionY();
////      this.motionZ = result.motionZ();
//      if (this.velocityChanged) {
//        this.velocityChanged = false;
//      }
//      this.collidedHorizontally = result.motionX() != motionX || result.motionZ() != motionZ;
//    }
//    this.onGround = result.onGround();

    // Renew location
    this.prevLocation = this.location.clone();
    this.location.add(this.motionX, this.motionY, this.motionZ);
    this.prevParentLocation = parentLocation;

    if (doBlockCollisions()) {
      if (startMotionX != this.motionX) {
        this.motionX = 0;
      }
      if (startMotionZ != this.motionZ) {
        this.motionZ = 0;
      }
    }

    updateHeadRotation(this.motionX, this.motionZ, distanceMoved(), parentLocation.getYaw());
    this.location.setYaw(this.rotationYaw);
    this.location.setPitch(this.rotationPitch);
  }

  public boolean shouldMove(Location parentLocation) {
    if (this.prevParentLocation == null) {
      return true;
    }
    return LocationUtils.distanceBetweenLocations(this.prevParentLocation, parentLocation) != 0;
  }

  public void registerTeleport(Location to) {
    this.location = to;
  }

  private void updateBotDistance() {
    double distance = ThreadLocalRandom.current().nextDouble(
      botDistance - BOT_DISTANCE_ADJUSTMENT,
      botDistance + BOT_DISTANCE_ADJUSTMENT
    );
    this.botDistance = Math.max(minBotDistance(), Math.min(maxBotDistance(), distance));
  }

  public double distanceMoved() {
    if (this.location == null || this.prevLocation == null) {
      return 0.0;
    }
    return LocationUtils.distanceBetweenLocations(this.location, this.prevLocation);
  }

  public double distanceToPlayer(Player player) {
    return LocationUtils.distanceBetweenLocations(player.getLocation(), this.location);
  }

  public double minBotDistance() {
    return 2.0;
  }

  public double maxBotDistance() {
    return 10.0;
  }

  public boolean moveOnTopOfPlayer() {
    return AccessHelper.now() - this.moveOnTopOfPlayerTime < 7000;
  }
}
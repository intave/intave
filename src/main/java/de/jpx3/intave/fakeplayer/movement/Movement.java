package de.jpx3.intave.fakeplayer.movement;

import de.jpx3.intave.tools.AccessHelper;
import de.jpx3.intave.world.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.world.collision.Collision;
import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
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
  public long moveOnTopOfPlayerTime;
  private Location prevParentLocation;
  private int lastCombatEvent = 100;

  Movement() {
  }

  protected abstract void move(Location parentLocation);

  public boolean doBlockCollisions() {
    return true;
  }

  public void combatEvent() {
    this.botDistance = 2.0;
    this.lastCombatEvent = 0;
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
    SimpleColliderSimulationResult result = collide(
      WrappedAxisAlignedBB.createFromPosition(location.getX(), location.getY(), location.getZ()),
      motionX, motionY, motionZ
    );
    if (doBlockCollisions()) {
//      this.motionX = result.motionX();
      this.motionY = result.motionY();
//      this.motionZ = result.motionZ();
      if (this.velocityChanged) {
        this.velocityChanged = false;
      }
      this.collidedHorizontally = result.motionX() != motionX || result.motionZ() != motionZ;
    }
    this.onGround = result.onGround();

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

  private SimpleColliderSimulationResult collide(WrappedAxisAlignedBB boundingBox, double motionX, double motionY, double motionZ) {
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(location.getWorld(), boundingBox.addCoord(motionX, motionY, motionZ));
    double startMotionY = motionY;
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionY = collisionBox.calculateYOffset(boundingBox, motionY);
    }
    boundingBox = (boundingBox.offset(0.0D, motionY, 0.0D));
    boolean onGround = startMotionY != motionY && startMotionY < 0.0D;
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionX = collisionBox.calculateXOffset(boundingBox, motionX);
    }
    boundingBox = boundingBox.offset(motionX, 0.0D, 0.0D);
    for (WrappedAxisAlignedBB collisionBox : collisionBoxes) {
      motionZ = collisionBox.calculateZOffset(boundingBox, motionZ);
    }
    return new SimpleColliderSimulationResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }

  public boolean shouldMove(Location parentLocation) {
    if (this.prevParentLocation == null) {
      return true;
    }
    return safeDistance(this.prevParentLocation, parentLocation) != 0;
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
    return safeDistance(this.location, this.prevLocation);
  }

  public double distanceToPlayer(Player player) {
    return safeDistance(player.getLocation(), this.location);
  }

  public double safeDistance(Location location1, Location location2) {
    if (location1.getWorld() != location2.getWorld()) {
      return 0.0;
    }
    return location1.distance(location2);
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
package de.jpx3.intave.player.fake.movement;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.ShapeResolver;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.BoundingBox;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static de.jpx3.intave.share.ClientMathHelper.floor;
import static de.jpx3.intave.share.Direction.Axis.*;

public abstract class Movement extends HeadRotationMovement {
  private static final double BOT_DISTANCE_ADJUSTMENT = 0.15;

  public double motionX = 0.0, motionY = 0.0, motionZ = 0.0;
  public Location location;
  public Location prevLocation;
  public boolean onGround = false;
  public boolean lastOnGround = false;
  public boolean collidedHorizontally;
  public int airTicks = 0;
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
      this.airTicks = 0;
    } else {
      this.airTicks++;
    }
    move(parentLocation);
    double startMotionX = this.motionX;
    double startMotionZ = this.motionZ;
    SimpleColliderResult result = collide(
      BoundingBox.fromPosition(location.getX(), location.getY(), location.getZ()),
      motionX, motionY, motionZ
    );
    if (doBlockCollisions()) {
      boolean collideHorizontally = collideHorizontally();
      if (collideHorizontally) {
        this.motionX = result.motionX();
        this.motionZ = result.motionZ();
      }
      this.motionY = result.motionY();
      if (this.velocityChanged) {
        this.velocityChanged = false;
      }
      this.collidedHorizontally = result.motionX() != motionX || result.motionZ() != motionZ;
    }
    this.lastOnGround = this.onGround;
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

    endTick();
  }

  public void endTick() {
  }

  private SimpleColliderResult collide(BoundingBox boundingBox, double motionX, double motionY, double motionZ) {
    List<BoundingBox> collisionBoxes = resolveCollisions(location.getWorld(), boundingBox.expand(motionX, motionY, motionZ));
    double startMotionY = motionY;
    for (BoundingBox collisionBox : collisionBoxes) {
      motionY = collisionBox.allowedOffset(Y_AXIS, boundingBox, motionY);
    }
    boundingBox = (boundingBox.offset(0.0D, motionY, 0.0D));
    boolean onGround = startMotionY != motionY && startMotionY < 0.0D;
    for (BoundingBox collisionBox : collisionBoxes) {
      motionX = collisionBox.allowedOffset(X_AXIS, boundingBox, motionX);
    }
    boundingBox = boundingBox.offset(motionX, 0.0D, 0.0D);
    for (BoundingBox collisionBox : collisionBoxes) {
      motionZ = collisionBox.allowedOffset(Z_AXIS, boundingBox, motionZ);
    }
    return new SimpleColliderResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }

  private static final ShapeResolverPipeline boundingBoxResolver = ShapeResolver.globalPipeline();

  private List<BoundingBox> resolveCollisions(World world, BoundingBox boundingBox) {
    int minX = floor(boundingBox.minX);
    int maxX = floor(boundingBox.maxX + 1.0D);
    int minY = floor(boundingBox.minY);
    int maxY = floor(boundingBox.maxY + 1.0D);
    int minZ = floor(boundingBox.minZ);
    int maxZ = floor(boundingBox.maxZ + 1.0D);
    int ystart = Math.max(minY - 1, 0);
    List<BoundingBox> resolvedBoundingBoxes = null;
    for (int chunkx = minX >> 4; chunkx <= maxX - 1 >> 4; ++chunkx) {
      int chunkXPos = chunkx << 4;
      for (int chunkz = minZ >> 4; chunkz <= maxZ - 1 >> 4; ++chunkz) {
        if (world.isChunkLoaded(chunkx, chunkz)) {
          int chunkZPos = chunkz << 4;
          int xstart = Math.max(minX, chunkXPos);
          int zstart = Math.max(minZ, chunkZPos);
          int xend = Math.min(maxX, chunkXPos + 16);
          int zend = Math.min(maxZ, chunkZPos + 16);
          for (int x = xstart; x < xend; ++x) {
            for (int z = zstart; z < zend; ++z) {
              for (int y = ystart; y < maxY; ++y) {
                Block block = VolatileBlockAccess.blockAccess(world, x, y, z);
                Material type = BlockTypeAccess.typeAccess(block);
                int variant = BlockVariantNativeAccess.variantAccess(block);
                List<BoundingBox> resolve = boundingBoxResolver.collisionShapeOf(world, null, type, variant, x, y, z).boundingBoxes();
                if ((resolve != null && !resolve.isEmpty())) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>(resolve);
                  } else {
                    resolvedBoundingBoxes.addAll(resolve);
                  }
                }
              }
            }
          }
        }
      }
    }
    if (resolvedBoundingBoxes == null) {
      resolvedBoundingBoxes = Collections.emptyList();
    } else {
      resolvedBoundingBoxes.removeIf(wrappedAxisAlignedBB -> !wrappedAxisAlignedBB.intersectsWith(boundingBox));
    }
    return resolvedBoundingBoxes;
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
    return location1.getWorld() != location2.getWorld() ? 0.0 : location1.distance(location2);
  }

  public double minBotDistance() {
    return 2.0;
  }

  public double maxBotDistance() {
    return 10.0;
  }

  public boolean moveOnTopOfPlayer() {
    return System.currentTimeMillis() - this.moveOnTopOfPlayerTime < 7000;
  }

  public boolean collideHorizontally() {
    return false;
  }
}
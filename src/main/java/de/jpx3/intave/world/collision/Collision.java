package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedBlockPosition;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.BlockAccessor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class Collision {
  public static List<WrappedAxisAlignedBB> resolve(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    int minX = WrappedMathHelper.floor(playerBoundingBox.minX);
    int maxX = WrappedMathHelper.floor(playerBoundingBox.maxX + 1.0D);
    int minY = WrappedMathHelper.floor(playerBoundingBox.minY);
    int maxY = WrappedMathHelper.floor(playerBoundingBox.maxY + 1.0D);
    int minZ = WrappedMathHelper.floor(playerBoundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(playerBoundingBox.maxZ + 1.0D);

    int ystart = Math.max(minY - 1, 0);

    List<WrappedAxisAlignedBB> resolvedBoundingBoxes = null;
    BoundingBoxAccess boundingBoxAccess = UserRepository.userOf(player).boundingBoxAccess();
    World world = player.getWorld();

    // this looks 1000x slower than it actually is
    for (int chunkx = minX >> 4; chunkx <= maxX - 1 >> 4; ++chunkx) {
      int chunkXPos = chunkx << 4;
      for (int chunkz = minZ >> 4; chunkz <= maxZ - 1 >> 4; ++chunkz) {
        if (world.isChunkLoaded(chunkx, chunkz)) {
          Chunk chunk = world.getChunkAt(chunkx, chunkz);
          int chunkZPos = chunkz << 4;
          int xstart = Math.max(minX, chunkXPos);
          int zstart = Math.max(minZ, chunkZPos);
          int xend = Math.min(maxX, chunkXPos + 16);
          int zend = Math.min(maxZ, chunkZPos + 16);
          for (int x = xstart; x < xend; ++x) {
            for (int z = zstart; z < zend; ++z) {
              for (int y = ystart; y < maxY; ++y) {
                List<WrappedAxisAlignedBB> resolve = boundingBoxAccess.resolve(chunk, x, y, z);
                if (resolve != null && !resolve.isEmpty()) {
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
      // filter invalid
      final Iterator<WrappedAxisAlignedBB> each = resolvedBoundingBoxes.iterator();
      while (each.hasNext()) {
        if (!each.next().intersectsWith(playerBoundingBox)) {
          each.remove();
        }
      }
    }
    return resolvedBoundingBoxes;
  }

  public static boolean playerInImaginaryBlock(User user, World world, int posX, int posY, int posZ, int type, int data) {
    List<WrappedAxisAlignedBB> boundingboxes =
      user.boundingBoxAccess().constructBlock(world, posX, posY, posZ, type, data);
    if(boundingboxes == null || boundingboxes.isEmpty()) {
      return false;
    }
    WrappedAxisAlignedBB playerBox = user.meta().movementData().boundingBox();
    for (WrappedAxisAlignedBB boundingbox : boundingboxes) {
      if (playerBox.intersectsWith(boundingbox)) {
        return true;
      }
    }
    return false;
  }

  public static boolean hasNoCollisions(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    return resolve(player, playerBoundingBox).isEmpty();
  }

  public static boolean nearBySolidBlock(Location location, double expansion) {
    for (double x = -expansion; x <= expansion; x += expansion) {
      for (double z = -expansion; z <= expansion; z += expansion) {
        Block block = BlockAccessor.blockAccess(location.clone().add(x, 0.0, z));
        if (block.getType().isSolid()) {
          return true;
        }
      }
    }
    return false;
  }

  public static boolean containsBlockInBB(
    World world,
    WrappedAxisAlignedBB playerBoundingBox,
    Material blockType
  ) {
    int minX = WrappedMathHelper.floor(playerBoundingBox.minX);
    int maxX = WrappedMathHelper.floor(playerBoundingBox.maxX);
    int minY = WrappedMathHelper.floor(playerBoundingBox.minY);
    int maxY = WrappedMathHelper.floor(playerBoundingBox.maxY);
    int minZ = WrappedMathHelper.floor(playerBoundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(playerBoundingBox.maxZ);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ ; z++) {
          Block block = BlockAccessor.blockAccess(world, x, y, z);
          if (block.getType() == blockType) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static WrappedAxisAlignedBB boundingBoxOf(
    User user,
    double positionX, double positionY, double positionZ
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    double width = movementData.widthRounded;
    float height = movementData.height;
    // 0.000000001 accuracy
    double newYMax = Math.round((positionY + height) * 1000000000d) / 1000000000d;
    return new WrappedAxisAlignedBB(
      positionX - width, positionY, positionZ - width,
      positionX + width, newYMax, positionZ + width
    );
  }

  public static WrappedAxisAlignedBB boundingBoxOf(
    User user, double width,
    double positionX, double positionY, double positionZ
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    double height = movementData.height;
    return new WrappedAxisAlignedBB(
      positionX - width, positionY, positionZ - width,
      positionX + width, positionY + height, positionZ + width
    );
  }

  public static WrappedAxisAlignedBB boundingBoxOf(User user, Location location) {
    return boundingBoxOf(user, location.getX(), location.getY(), location.getZ());
  }

  public static WrappedAxisAlignedBB boundingBoxOf(Location center) {
    return boundingBoxOf(center.getX(), center.getY(), center.getZ());
  }

  public static WrappedAxisAlignedBB boundingBoxOf(WrappedBlockPosition position) {
    return boundingBoxOf(position.xCoord, position.yCoord, position.zCoord);
  }

  private final static float PLAYER_HEIGHT = 1.8f;
  private final static double HALF_WIDTH = 0.3;

  @Deprecated
  public static WrappedAxisAlignedBB boundingBoxOf(
    double positionX, double positionY, double positionZ
  ) {
    return new WrappedAxisAlignedBB(
      positionX - HALF_WIDTH, positionY, positionZ - HALF_WIDTH,
      positionX + HALF_WIDTH, positionY + PLAYER_HEIGHT, positionZ + HALF_WIDTH
    );
  }

  public static CollisionResult resolveQuickCollisions(
    Player player,
    double positionX, double positionY, double positionZ,
    double motionX, double motionY, double motionZ
  ) {
    WrappedAxisAlignedBB boundingBox = boundingBoxOf(positionX, positionY, positionZ);
    List<WrappedAxisAlignedBB> collisionBoxes = Collision.resolve(
      player,
      boundingBox.addCoord(motionX, motionY, motionZ)
    );
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
    return new CollisionResult(motionX, motionY, motionZ, onGround, startMotionY != motionY);
  }

  public static boolean checkBoundingBoxIntersection(User user, WrappedAxisAlignedBB boundingBox) {
    return !Collision.resolve(user.player(), boundingBox).isEmpty();
  }

  public static class CollisionResult {
    private final double motionX, motionY, motionZ;
    private final boolean onGround, collidedVertically;

    public CollisionResult(double motionX, double motionY, double motionZ, boolean onGround, boolean collidedVertically) {
      this.motionX = motionX;
      this.motionY = motionY;
      this.motionZ = motionZ;
      this.onGround = onGround;
      this.collidedVertically = collidedVertically;
    }

    public double motionX() {
      return motionX;
    }

    public double motionY() {
      return motionY;
    }

    public double motionZ() {
      return motionZ;
    }

    public boolean onGround() {
      return onGround;
    }

    public boolean collidedVertically() {
      return collidedVertically;
    }
  }
}
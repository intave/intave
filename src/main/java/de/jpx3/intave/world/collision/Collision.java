package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.client.MaterialLogic;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Relocate
public final class Collision {
  private final static BoundingBoxResolver boundingBoxResolver = BoundingBoxAccess.globalBoundingBoxResolver();

  public static List<WrappedAxisAlignedBB> resolve(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    int minX = WrappedMathHelper.floor(playerBoundingBox.minX);
    int maxX = WrappedMathHelper.floor(playerBoundingBox.maxX + 1.0D);
    int minY = WrappedMathHelper.floor(playerBoundingBox.minY);
    int maxY = WrappedMathHelper.floor(playerBoundingBox.maxY + 1.0D);
    int minZ = WrappedMathHelper.floor(playerBoundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(playerBoundingBox.maxZ + 1.0D);

    int ystart = Math.max(minY - 1, 0);

    List<WrappedAxisAlignedBB> resolvedBoundingBoxes = null;
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();

    boolean outsideBorderLast = movementData.outsideBorder;
    boolean outsideBorderCurrent = playerOutsideBorder(user);

    if (outsideBorderLast && outsideBorderCurrent) {
      movementData.outsideBorder = false;
    } else if (!outsideBorderLast && !outsideBorderCurrent) {
      movementData.outsideBorder = true;
    }

    BoundingBoxAccess boundingBoxAccess = user.boundingBoxAccess();
    World world = player.getWorld();

    // this looks 1000x slower than it actually is
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
                List<WrappedAxisAlignedBB> resolve = boundingBoxAccess.resolveBoxes(chunkx, chunkz, x, y, z);

                boolean positionInBorder = !blockOutsideBorder(world, x, z);
                if (positionInBorder && !movementData.outsideBorder) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>();
                  }
                  resolvedBoundingBoxes.add(new WrappedAxisAlignedBB(x, y, z, x + 1, y, z + 1));
                }

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
      resolvedBoundingBoxes.removeIf(wrappedAxisAlignedBB -> !wrappedAxisAlignedBB.intersectsWith(playerBoundingBox));
    }
    return resolvedBoundingBoxes;
  }

  @Deprecated
  // this is not really performant - please remove me ~richy
  public static List<WrappedAxisAlignedBB> resolve(
    World world,
    WrappedAxisAlignedBB boundingBox
  ) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX + 1.0D);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY + 1.0D);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ + 1.0D);

    int ystart = Math.max(minY - 1, 0);
    List<WrappedAxisAlignedBB> resolvedBoundingBoxes = null;

    // this looks 1000x slower than it actually is
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
                Material type = BukkitBlockAccess.blockAccess(world, x, y, z).getType();
                List<WrappedAxisAlignedBB> resolve = boundingBoxResolver.resolve(world, type, x, y, z);

                boolean insideBorder = !blockOutsideBorder(world, x, z);
                if (insideBorder) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>();
                  }
                  resolvedBoundingBoxes.add(new WrappedAxisAlignedBB(x, y, z, x + 1, y, z + 1));
                }

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

  private static boolean blockOutsideBorder(World world, double positionX, double positionZ) {
    WorldBorder worldBorder = world.getWorldBorder();
    Location center = worldBorder.getCenter();
    double radians = worldBorder.getSize() / 2.0;
    double minX = center.getX() - radians - 1;
    double minZ = center.getZ() - radians - 1;
    double maxX = center.getX() + radians;
    double maxZ = center.getZ() + radians;
    return positionX > minX && positionX < maxX && positionZ > minZ && positionZ < maxZ;
  }

  private static boolean playerOutsideBorder(User user) {
    World world = user.player().getWorld();
    UserMetaMovementData movementData = user.meta().movementData();
    double positionX = movementData.verifiedPositionX;
    double positionZ = movementData.verifiedPositionZ;
    WorldBorder worldBorder = world.getWorldBorder();
    Location center = worldBorder.getCenter();
    double radians = worldBorder.getSize() / 2.0;
    double minX = center.getX() - radians;
    double minZ = center.getZ() - radians;
    double maxX = center.getX() + radians;
    double maxZ = center.getZ() + radians;
    if (movementData.outsideBorder) {
      minX++;
      minZ++;
      maxX--;
      maxZ--;
    } else {
      minX--;
      minZ--;
      maxX++;
      maxZ++;
    }
    return positionX > minX && positionX < maxX && positionZ > minZ && positionZ < maxZ;
  }

  public static boolean playerInImaginaryBlock(User user, World world, int posX, int posY, int posZ, Material type, int data) {
    List<WrappedAxisAlignedBB> boundingBoxes = user.boundingBoxAccess().constructBlock(world, posX, posY, posZ, type, data);
    if (boundingBoxes == null || boundingBoxes.isEmpty()) {
      return false;
    }
    WrappedAxisAlignedBB playerBox = user.meta().movementData().boundingBox();
    return boundingBoxes.stream().anyMatch(playerBox::intersectsWith);
  }

  public static boolean hasNoCollisions(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    return resolve(player, playerBoundingBox).isEmpty();
  }

  public static boolean nearBySolidBlock(
    World world,
    WrappedAxisAlignedBB boundingBox
  ) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          Block block = BukkitBlockAccess.blockAccess(world, x, y, z);
          Material type = block.getType();
          if (!MaterialLogic.isLiquid(type) && block.getType() != Material.AIR) {
            return true;
          }
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
        for (int z = minZ; z <= maxZ; z++) {
          Block block = BukkitBlockAccess.blockAccess(world, x, y, z);
          if (block.getType() == blockType) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean checkBoundingBoxIntersection(User user, WrappedAxisAlignedBB boundingBox) {
    return !Collision.resolve(user.player(), boundingBox).isEmpty();
  }
}
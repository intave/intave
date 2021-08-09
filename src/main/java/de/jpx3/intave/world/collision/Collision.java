package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.tools.annotate.Relocate;
import de.jpx3.intave.tools.client.Materials;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.blockaccess.BlockDataAccess;
import de.jpx3.intave.world.blockaccess.BlockTypeAccess;
import de.jpx3.intave.world.blockaccess.BukkitBlockAccess;
import de.jpx3.intave.world.blockshape.OCBlockShapeAccess;
import de.jpx3.intave.world.blockshape.resolver.BoundingBoxResolver;
import de.jpx3.intave.world.blockshape.resolver.pipeline.ResolverPipeline;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Relocate
@DoNotFlowObfuscate
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
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movementData();

    boolean outsideBorderLast = movementData.outsideBorder;
    boolean outsideBorderCurrent = playerOutsideBorder(user);

    if (outsideBorderLast && outsideBorderCurrent) {
      movementData.outsideBorder = false;
    } else if (!outsideBorderLast && !outsideBorderCurrent) {
      movementData.outsideBorder = true;
    }

    OCBlockShapeAccess blockShapeAccess = user.blockShapeAccess();
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
                List<WrappedAxisAlignedBB> resolve = blockShapeAccess.resolveBoxes(chunkx, chunkz, x, y, z);
                Material material = blockShapeAccess.resolveType(chunkx, chunkz, x, y, z);
                if (CollisionModifiers.isModified(material)) {
                  resolve = CollisionModifiers.modified(material, user, playerBoundingBox, x, y, z, resolve);
                }
                boolean blockOutsideBorder = !blockInsideBorder(world, x, z);
                if (blockOutsideBorder && !movementData.outsideBorder) {
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

  private final static ResolverPipeline boundingBoxResolver = BoundingBoxResolver.pipelineHead();

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
                Block block = BukkitBlockAccess.blockAccess(world, x, y, z);
                Material type = BlockTypeAccess.typeAccess(block);
                int data = BlockDataAccess.dataAccess(block);
                List<WrappedAxisAlignedBB> resolve = boundingBoxResolver.resolve(world, null, type, data, x, y, z);
                boolean blockIsOutsideBorder = !blockInsideBorder(world, x, z);
                if (blockIsOutsideBorder) {
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

  public static boolean hasNoCollisions(User user, WrappedAxisAlignedBB boundingBox) {
    return resolve(user.player(), boundingBox).isEmpty();
  }

  public static boolean blockInsideBorder(World world, double positionX, double positionZ) {
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
    MovementMetadata movementData = user.meta().movementData();
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
    List<WrappedAxisAlignedBB> boundingBoxes = user.blockShapeAccess().constructBlock(world, posX, posY, posZ, type, data);
    if (boundingBoxes == null || boundingBoxes.isEmpty()) {
      return false;
    }
    WrappedAxisAlignedBB playerBox = user.meta().movementData().boundingBox();
    playerBox = playerBox.shrink(0.001);
    return boundingBoxes.stream().anyMatch(playerBox::intersectsWith);
  }

  public static boolean isInsideBlocks(Player player, WrappedAxisAlignedBB playerBoundingBox) {
    return !isNotInsideBlocks(player, playerBoundingBox);
  }

  public static boolean isNotInsideBlocks(Player player, WrappedAxisAlignedBB playerBoundingBox) {
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
          Material type = BlockTypeAccess.typeAccess(block);
          if (!Materials.isLiquid(type) && BlockTypeAccess.typeAccess(block) != Material.AIR) {
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
    return containsBlockInBB(world, playerBoundingBox, material -> material == blockType);
  }

  public static boolean containsBlockInBB(
    World world,
    WrappedAxisAlignedBB playerBoundingBox,
    Function<Material, Boolean> blockTypeApplier
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
          if (blockTypeApplier.apply(BlockTypeAccess.typeAccess(block))) {
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
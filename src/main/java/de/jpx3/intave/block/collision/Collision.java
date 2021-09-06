package de.jpx3.intave.block.collision;

import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.BlockTypeAccess;
import de.jpx3.intave.block.access.BlockVariantAccess;
import de.jpx3.intave.block.access.BukkitBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShapeAccess;
import de.jpx3.intave.block.shape.boxresolver.BoundingBoxResolver;
import de.jpx3.intave.block.shape.boxresolver.ResolverPipeline;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.border.WorldBorders;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@Relocate
@DoNotFlowObfuscate
public final class Collision {
  public static List<BoundingBox> resolve(Player player, BoundingBox playerBoundingBox) {
    int minX = WrappedMathHelper.floor(playerBoundingBox.minX);
    int maxX = WrappedMathHelper.floor(playerBoundingBox.maxX + 1.0D);
    int minY = WrappedMathHelper.floor(playerBoundingBox.minY);
    int maxY = WrappedMathHelper.floor(playerBoundingBox.maxY + 1.0D);
    int minZ = WrappedMathHelper.floor(playerBoundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(playerBoundingBox.maxZ + 1.0D);

    int ystart = Math.max(minY - 1, 0);

    List<BoundingBox> resolvedBoundingBoxes = null;
    User user = UserRepository.userOf(player);
    MovementMetadata movementData = user.meta().movement();

    boolean outsideBorderLast = movementData.outsideBorder;
    boolean outsideBorderCurrent = playerOutsideBorder(user);

    if (outsideBorderLast && outsideBorderCurrent) {
      movementData.outsideBorder = false;
    } else if (!outsideBorderLast && !outsideBorderCurrent) {
      movementData.outsideBorder = true;
    }

    BlockShapeAccess blockShapeAccess = user.blockShapeAccess();
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
                List<BoundingBox> resolve = blockShapeAccess.resolveBoxes(chunkx, chunkz, x, y, z);
                Material material = blockShapeAccess.resolveType(chunkx, chunkz, x, y, z);
                if (CollisionModifiers.isModified(material)) {
                  resolve = CollisionModifiers.modified(material, user, playerBoundingBox, x, y, z, resolve);
                }
                boolean blockOutsideBorder = !blockInsideBorder(world, x, z);
                if (blockOutsideBorder && !movementData.outsideBorder) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>();
                  }
                  resolvedBoundingBoxes.add(new BoundingBox(x, y, z, x + 1, y, z + 1));
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
      if (resolvedBoundingBoxes.isEmpty()) {
        resolvedBoundingBoxes = Collections.emptyList();
      }
    }
    return resolvedBoundingBoxes;
  }

  private final static ResolverPipeline boundingBoxResolver = BoundingBoxResolver.pipelineHead();

  @Deprecated
  // this is not really performant - please remove me ~richy
  public static List<BoundingBox> resolve(
    World world,
    BoundingBox boundingBox
  ) {
    int minX = WrappedMathHelper.floor(boundingBox.minX);
    int maxX = WrappedMathHelper.floor(boundingBox.maxX + 1.0D);
    int minY = WrappedMathHelper.floor(boundingBox.minY);
    int maxY = WrappedMathHelper.floor(boundingBox.maxY + 1.0D);
    int minZ = WrappedMathHelper.floor(boundingBox.minZ);
    int maxZ = WrappedMathHelper.floor(boundingBox.maxZ + 1.0D);
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
                Block block = BukkitBlockAccess.blockAccess(world, x, y, z);
                Material type = BlockTypeAccess.typeAccess(block);
                int variant = BlockVariantAccess.variantAccess(block);
                List<BoundingBox> resolve = boundingBoxResolver.resolve(world, null, type, variant, x, y, z);
                boolean blockIsOutsideBorder = !blockInsideBorder(world, x, z);
                if (blockIsOutsideBorder) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>();
                  }
                  resolvedBoundingBoxes.add(new BoundingBox(x, y, z, x + 1, y, z + 1));
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

  public static boolean hasNoCollisions(User user, BoundingBox boundingBox) {
    return resolve(user.player(), boundingBox).isEmpty();
  }

  public static boolean blockInsideBorder(World world, double positionX, double positionZ) {
    Location center = WorldBorders.centerOfWorldBorderIn(world);
    double radius = WorldBorders.sizeOfWorldBorderIn(world)/ 2.0;
    double minX = center.getX() - radius - 1;
    double minZ = center.getZ() - radius - 1;
    double maxX = center.getX() + radius;
    double maxZ = center.getZ() + radius;
    return positionX > minX && positionX < maxX && positionZ > minZ && positionZ < maxZ;
  }

  private static boolean playerOutsideBorder(User user) {
    World world = user.player().getWorld();
    MovementMetadata movementData = user.meta().movement();
    double positionX = movementData.verifiedPositionX;
    double positionZ = movementData.verifiedPositionZ;
    Location center = WorldBorders.centerOfWorldBorderIn(world);
    double radius = WorldBorders.sizeOfWorldBorderIn(world)/ 2.0;
    double minX = center.getX() - radius;
    double minZ = center.getZ() - radius;
    double maxX = center.getX() + radius;
    double maxZ = center.getZ() + radius;
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
    List<BoundingBox> boundingBoxes = boundingBoxResolver.resolve(world, user.player(), type, data, posX, posY, posZ);
    if (boundingBoxes == null || boundingBoxes.isEmpty()) {
      return false;
    }
    BoundingBox playerBox = user.meta().movement().boundingBox();
    playerBox = playerBox.shrink(0.001);
    return boundingBoxes.stream().anyMatch(playerBox::intersectsWith);
  }

  public static boolean isInsideBlocks(Player player, BoundingBox playerBoundingBox) {
    return !isNotInsideBlocks(player, playerBoundingBox);
  }

  public static boolean isNotInsideBlocks(Player player, BoundingBox playerBoundingBox) {
    return resolve(player, playerBoundingBox).isEmpty();
  }

  public static boolean nearBySolidBlock(
    World world,
    BoundingBox boundingBox
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
          if (!MaterialMagic.isLiquid(type) && BlockTypeAccess.typeAccess(block) != Material.AIR) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static boolean containsBlockInBB(
    World world,
    BoundingBox playerBoundingBox,
    Material blockType
  ) {
    return containsBlockInBB(world, playerBoundingBox, material -> material == blockType);
  }

  public static boolean containsBlockInBB(
    World world,
    BoundingBox playerBoundingBox,
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

  public static boolean checkBoundingBoxIntersection(User user, BoundingBox boundingBox) {
    return !Collision.resolve(user.player(), boundingBox).isEmpty();
  }
}
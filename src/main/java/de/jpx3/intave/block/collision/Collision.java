package de.jpx3.intave.block.collision;

import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeCombiner;
import de.jpx3.intave.block.shape.ShapeResolver;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.state.BlockStateAccess;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantAccess;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.WorldHeight;
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

import static de.jpx3.intave.shade.ClientMathHelper.floor;

@Relocate
@DoNotFlowObfuscate
public final class Collision {
  // usually we collide with 8 blocks, so a limit of 256 comes with a very big margin
  private static final int COLLISION_LIMIT = 256;

  public static BlockShape collisionShape(Player player, BoundingBox playerBoundingBox) {
    int minX = floor(playerBoundingBox.minX);
    int maxX = floor(playerBoundingBox.maxX);
    int minY = floor(playerBoundingBox.minY);
    int maxY = floor(playerBoundingBox.maxY);
    int minZ = floor(playerBoundingBox.minZ);
    int maxZ = floor(playerBoundingBox.maxZ);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
    User user = UserRepository.userOf(player);
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    ShapeCombiner shapeCombiner = ShapeCombiner.create();
    BlockStateAccess stateAccess = user.blockStates();
    boolean outsideBorderLast = movementData.outsideBorder;
    boolean outsideBorderCurrent = playerOutsideBorder(user);
    if (outsideBorderLast && outsideBorderCurrent) {
      movementData.outsideBorder = false;
    } else if (!outsideBorderLast && !outsideBorderCurrent) {
      movementData.outsideBorder = true;
    }
    int blocksRemaining = COLLISION_LIMIT;
    exit:
    for (int x = minX; x <= maxX; ++x) {
      for (int z = minZ; z <= maxZ; ++z) {
        for (int y = ystart; y <= maxY; ++y) {
          if (blocksRemaining-- <= 0) {
            break exit;
          }
          BlockShape resolve = stateAccess.shapeAt(x, y, z);
          Material material = stateAccess.typeAt(x, y, z);
          if (CollisionModifiers.isModified(material)) {
            // this should not happen too often
            resolve = CollisionModifiers.modified(material, user, playerBoundingBox, x, y, z, resolve);
          }
          boolean blockOutsideBorder = !blockInsideBorder(world, x, z);
          if (blockOutsideBorder && !movementData.outsideBorder) {
            BoundingBox borderShape = BoundingBox.fromBounds(x, y, z, x + 1, y, z + 1);
            if (borderShape.intersectsWith(playerBoundingBox)) {
              shapeCombiner = shapeCombiner.append(borderShape);
            }
          }
          if (resolve.intersectsWith(playerBoundingBox)) {
            shapeCombiner = shapeCombiner.append(resolve);
          }
        }
      }
    }
    return shapeCombiner.compile();
  }

  public static boolean present(Player player, BoundingBox playerBox) {
    return !nonePresent(player, playerBox);
  }

  public static boolean unsafePresent(World world, Player player, BoundingBox playerBox) {
    return !unsafeNonePresent(world, player, playerBox);
  }

  public static boolean nonePresent(Player player, BoundingBox playerBox) {
    int minX = floor(playerBox.minX);
    int maxX = floor(playerBox.maxX);
    int minY = floor(playerBox.minY);
    int maxY = floor(playerBox.maxY);
    int minZ = floor(playerBox.minZ);
    int maxZ = floor(playerBox.maxZ);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
    User user = UserRepository.userOf(player);
    World world = player.getWorld();
    BlockStateAccess blockStates = user.blockStates();
    MovementMetadata movementData = user.meta().movement();
    boolean outsideBorderLast = movementData.outsideBorder;
    boolean outsideBorderCurrent = playerOutsideBorder(user);
    if (outsideBorderLast && outsideBorderCurrent) {
      movementData.outsideBorder = false;
    } else if (!outsideBorderLast && !outsideBorderCurrent) {
      movementData.outsideBorder = true;
    }
    int blockRemaining = COLLISION_LIMIT;
    exit:
    for (int x = minX; x <= maxX; ++x) {
      for (int z = minZ; z <= maxZ; ++z) {
        for (int y = ystart; y <= maxY; ++y) {
          if (blockRemaining-- <= 0) {
            break exit;
          }
          BlockShape shape = blockStates.shapeAt(x, y, z);
          Material material = blockStates.typeAt(x, y, z);
          if (CollisionModifiers.isModified(material)) {
            shape = CollisionModifiers.modified(material, user, playerBox, x, y, z, shape);
          }
          if (shape.intersectsWith(playerBox)) {
            return false;
          }
          boolean blockOutsideBorder = !blockInsideBorder(world, x, z);
          if (blockOutsideBorder && !movementData.outsideBorder) {
            if (intersects(playerBox, x, y, z, x + 1, y, z + 1)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  public static boolean unsafeNonePresent(World world, Player player, BoundingBox playerBox) {
    int minX = floor(playerBox.minX);
    int maxX = floor(playerBox.maxX);
    int minY = floor(playerBox.minY);
    int maxY = floor(playerBox.maxY);
    int minZ = floor(playerBox.minZ);
    int maxZ = floor(playerBox.maxZ);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
    int blockRemaining = COLLISION_LIMIT;
    exit:
    for (int x = minX; x <= maxX; ++x) {
      for (int z = minZ; z <= maxZ; ++z) {
        for (int y = ystart; y <= maxY; ++y) {
          if (blockRemaining-- <= 0) {
            break exit;
          }
          Block block = VolatileBlockAccess.blockAccess(world, x, y, z);
          Material type = BlockTypeAccess.typeAccess(block, player);
          int variant = BlockVariantAccess.variantAccess(block);
          BlockShape shape = shapeResolver.resolve(world, player, type, variant, x, y, z);
          if (shape.intersectsWith(playerBox)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean intersects(BoundingBox boundingBox, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    return boundingBox.maxX > minX && boundingBox.minX < maxX && (boundingBox.maxY > minY && boundingBox.minY < maxY && boundingBox.maxZ > minZ && boundingBox.minZ < maxZ);
  }

  private final static ShapeResolverPipeline shapeResolver = ShapeResolver.pipelineHead();

  @Deprecated
  // I suck, please remove
  public static List<BoundingBox> __INVALID__resolveBoxes(Player player, BoundingBox playerBoundingBox) {
    int minX = floor(playerBoundingBox.minX);
    int maxX = floor(playerBoundingBox.maxX + 1.0D);
    int minY = floor(playerBoundingBox.minY);
    int maxY = floor(playerBoundingBox.maxY + 1.0D);
    int minZ = floor(playerBoundingBox.minZ);
    int maxZ = floor(playerBoundingBox.maxZ + 1.0D);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
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
    BlockStateAccess stateAccess = user.blockStates();
    World world = player.getWorld();
    int blockRemaining = COLLISION_LIMIT;
    exit:
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
                if (blockRemaining-- <= 0) {
                  break exit;
                }
                BlockShape blockShape = stateAccess.shapeAt(x, y, z);
//                List<BoundingBox> resolve = blockShape.boundingBoxes();
                Material material = stateAccess.typeAt(x, y, z);
                if (CollisionModifiers.isModified(material)) {
                  blockShape = CollisionModifiers.modified(material, user, playerBoundingBox, x, y, z, blockShape);
                }
                boolean blockOutsideBorder = !blockInsideBorder(world, x, z);
                if (blockOutsideBorder && !movementData.outsideBorder) {
                  if (resolvedBoundingBoxes == null) {
                    resolvedBoundingBoxes = new ArrayList<>();
                  }
                  resolvedBoundingBoxes.add(new BoundingBox(x, y, z, x + 1, y, z + 1));
                }
                if (blockShape != null && !blockShape.isEmpty()) {
                  if (blockShape.intersectsWith(playerBoundingBox)) {
                    if (resolvedBoundingBoxes == null) {
                      resolvedBoundingBoxes = new ArrayList<>(blockShape.boundingBoxes());
                    } else {
                      resolvedBoundingBoxes.addAll(blockShape.boundingBoxes());
                    }
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
      resolvedBoundingBoxes.removeIf(boundingBox -> !boundingBox.intersectsWith(playerBoundingBox));
      if (resolvedBoundingBoxes.isEmpty()) {
        resolvedBoundingBoxes = Collections.emptyList();
      }
    }
    return resolvedBoundingBoxes;
  }

  public static boolean blockInsideBorder(World world, double positionX, double positionZ) {
    Location center = WorldBorders.centerOfWorldBorderIn(world);
    double radius = WorldBorders.sizeOfWorldBorderIn(world) / 2.0;
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
    BlockShape boundingBoxes = shapeResolver.resolve(world, user.player(), type, data, posX, posY, posZ);
    if (boundingBoxes == null || boundingBoxes.isEmpty()) {
      return false;
    }
    BoundingBox playerBox = user.meta().movement().boundingBox();
    playerBox = playerBox.shrink(0.001); // :(
    return boundingBoxes.intersectsWith(playerBox);
  }

  public static boolean nearSolidBlock(
    World world,
    BoundingBox boundingBox
  ) {
    int minX = floor(boundingBox.minX);
    int maxX = floor(boundingBox.maxX);
    int minY = floor(boundingBox.minY);
    int maxY = floor(boundingBox.maxY);
    int minZ = floor(boundingBox.minZ);
    int maxZ = floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          Block block = VolatileBlockAccess.blockAccess(world, x, y, z);
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
    int minX = floor(playerBoundingBox.minX);
    int maxX = floor(playerBoundingBox.maxX);
    int minY = floor(playerBoundingBox.minY);
    int maxY = floor(playerBoundingBox.maxY);
    int minZ = floor(playerBoundingBox.minZ);
    int maxZ = floor(playerBoundingBox.maxZ);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          Block block = VolatileBlockAccess.blockAccess(world, x, y, z);
          if (blockTypeApplier.apply(BlockTypeAccess.typeAccess(block))) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
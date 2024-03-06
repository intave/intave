package de.jpx3.intave.block.collision;

import de.jpx3.intave.annotate.DoNotFlowObfuscate;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.entity.StaticEntityCollisions;
import de.jpx3.intave.block.collision.modifier.CollisionModifiers;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.resolve.ShapeResolver;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.block.type.BlockTypeAccess;
import de.jpx3.intave.block.variant.BlockVariantNativeAccess;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Position;
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
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static de.jpx3.intave.share.ClientMath.floor;

@Relocate
@DoNotFlowObfuscate
public final class Collision {
  // usually we collide with 8 blocks, so a limit of 32 comes with a very big margin
  private static final int COLLISION_CHECK_LIMIT = 256;

  private static final Collector<BlockShape, ?, BlockShape> SHAPE_COMPILATION =
    Collectors.collectingAndThen(Collectors.toList(), BlockShapes::merge);

  private static final Collector<BlockShape, ?, Boolean> EXISTS_ANY_SHAPE = shapeCountMatch(s -> s > 0);
  private static final Collector<BlockShape, ?, Boolean> EXISTS_NO_SHAPE = shapeCountMatch(s -> s == 0);

  private static Collector<BlockShape, ?, Boolean> shapeCountMatch(LongPredicate predicate) {
    return Collectors.collectingAndThen(Collectors.counting(), predicate::test);
  }

  public static BlockShape shape(Player player, BoundingBox playerBoundingBox) {
    return collectCollisionShapes(player, playerBoundingBox, COLLISION_CHECK_LIMIT, SHAPE_COMPILATION, BlockShapes::emptyShape);
  }

  public static boolean present(Player player, BoundingBox playerBox) {
    return collectCollisionShapes(player, playerBox, 1, EXISTS_ANY_SHAPE, () -> false);
  }

  public static boolean nonePresent(Player player, BoundingBox playerBox) {
    return collectCollisionShapes(player, playerBox, 1, EXISTS_NO_SHAPE, () -> true);
  }

  public static <C, R> R collectCollisionShapes(
    Player player,
    BoundingBox playerBox,
    int collisionLimit,
    Collector<BlockShape, C, R> primaryCollector,
    Supplier<? extends R> escapeReturn
  ) {
    Supplier<C> containerSupplier = primaryCollector.supplier();
    C container = null;
    BiConsumer<C, BlockShape> accumulator = primaryCollector.accumulator();
    Function<C, R> finisher = primaryCollector.finisher();
    int minX = floor(playerBox.minX);
    int maxX = floor(playerBox.maxX);
    int minY = floor(playerBox.minY);
    int maxY = floor(playerBox.maxY);
    int minZ = floor(playerBox.minZ);
    int maxZ = floor(playerBox.maxZ);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
    User user = UserRepository.userOf(player);
    World world = player.getWorld();
    MovementMetadata movementData = user.meta().movement();
    ExtendedBlockStateCache stateAccess = user.blockStates();
    boolean outsideBorderLast = movementData.outsideBorder;
    boolean outsideBorderCurrent = playerOutsideBorder(user);
    // this works, but why?
    if (outsideBorderLast && outsideBorderCurrent) {
      movementData.outsideBorder = false;
    } else if (!outsideBorderLast && !outsideBorderCurrent) {
      movementData.outsideBorder = true;
    }
    int collisionChecksRemaining = COLLISION_CHECK_LIMIT;
    int collisionsRemaining = Math.min(collisionLimit, COLLISION_CHECK_LIMIT);
    exit:
    for (int x = minX; x <= maxX; ++x) {
      for (int z = minZ; z <= maxZ; ++z) {
        for (int y = ystart; y <= maxY; ++y) {
          if (collisionChecksRemaining-- <= 0) {
            break exit;
          }
          BlockShape resolve = stateAccess.collisionShapeAt(x, y, z);
          Material material = stateAccess.typeAt(x, y, z);
          if (CollisionModifiers.isModified(material)) {
            // this should not happen too often
            resolve = CollisionModifiers.modified(user, playerBox, material, x, y, z, resolve, CollisionOrigin.MOTION_CALCULATION);
          }

          // can only happen when the underlying block is air
          if (material == Material.AIR) {
            BlockShape entityInducedShape = StaticEntityCollisions.inducedEntityShape(user, x, y, z);
            if (!entityInducedShape.isEmpty() && entityInducedShape.intersectsWith(playerBox)) {
              if (container == null) {
                container = containerSupplier.get();
              }
              accumulator.accept(container, entityInducedShape);
              if (--collisionsRemaining <= 0) {
                return finisher.apply(container);
              }
            }
          }

          boolean blockOutsideBorder = !blockInsideBorder(world, x, z);
          if (blockOutsideBorder && !movementData.outsideBorder) {
            BoundingBox borderShape = BoundingBox.fromBounds(x, y, z, x + 1, y, z + 1);
            if (borderShape.intersectsWith(playerBox)) {
              if (container == null) {
                container = containerSupplier.get();
              }
              accumulator.accept(container, borderShape);
              if (--collisionsRemaining <= 0) {
                return finisher.apply(container);
              }
            }
          }

          if (resolve.intersectsWith(playerBox)) {
            if (container == null) {
              container = containerSupplier.get();
            }
            accumulator.accept(container, resolve);
            if (--collisionsRemaining <= 0) {
              return finisher.apply(container);
            }
          }
        }
      }
    }
    return container == null ? escapeReturn.get() : finisher.apply(container);
  }

  public static <C, R> R collectCollidingPositions(
    Player player,
    BoundingBox playerBox,
    int collisionLimit,
    Collector<Position, C, R> collector
  ) {
    C container = collector.supplier().get();
    BiConsumer<C, Position> accumulator = collector.accumulator();
    Function<C, R> finisher = collector.finisher();
    int minX = floor(playerBox.minX);
    int maxX = floor(playerBox.maxX);
    int minY = floor(playerBox.minY);
    int maxY = floor(playerBox.maxY);
    int minZ = floor(playerBox.minZ);
    int maxZ = floor(playerBox.maxZ);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
    User user = UserRepository.userOf(player);
    ExtendedBlockStateCache stateAccess = user.blockStates();
    int blocksRemaining = COLLISION_CHECK_LIMIT;
    int collisionsRemaining = Math.min(collisionLimit, COLLISION_CHECK_LIMIT);
    exit:
    for (int x = minX; x <= maxX; ++x) {
      for (int z = minZ; z <= maxZ; ++z) {
        for (int y = ystart; y <= maxY; ++y) {
          if (blocksRemaining-- <= 0) {
            break exit;
          }
          BlockShape resolve = stateAccess.collisionShapeAt(x, y, z);
          if (resolve.intersectsWith(playerBox)) {
            accumulator.accept(container, new Position(x, y, z));
            if (--collisionsRemaining <= 0) {
              return finisher.apply(container);
            }
          }
        }
      }
    }
    return finisher.apply(container);
  }

  @Deprecated
  public static boolean unsafePresent(World world, Player player, BoundingBox playerBox) {
    return !unsafeNonePresent(world, player, playerBox);
  }

  @Deprecated
  public static boolean unsafeNonePresent(World world, Player player, BoundingBox playerBox) {
    int minX = floor(playerBox.minX);
    int maxX = floor(playerBox.maxX);
    int minY = floor(playerBox.minY);
    int maxY = floor(playerBox.maxY);
    int minZ = floor(playerBox.minZ);
    int maxZ = floor(playerBox.maxZ);
    int ystart = Math.max(minY - 1, WorldHeight.LOWER_WORLD_LIMIT);
    int blockRemaining = COLLISION_CHECK_LIMIT;
    exit:
    for (int x = minX; x <= maxX; ++x) {
      for (int z = minZ; z <= maxZ; ++z) {
        for (int y = ystart; y <= maxY; ++y) {
          if (blockRemaining-- <= 0) {
            break exit;
          }
          Block block = VolatileBlockAccess.blockAccess(world, x, y, z);
          Material type = BlockTypeAccess.typeAccess(block, player);
          int variant = BlockVariantNativeAccess.variantAccess(block);
          BlockShape shape = SHAPE_RESOLVER.collisionShapeOf(world, player, type, variant, x, y, z);
          if (shape.intersectsWith(playerBox)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean intersects(
    BoundingBox boundingBox, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    return boundingBox.maxX > minX
      && boundingBox.minX < maxX
      && (boundingBox.maxY > minY
      && boundingBox.minY < maxY
      && boundingBox.maxZ > minZ
      && boundingBox.minZ < maxZ);
  }

  private static final ShapeResolverPipeline SHAPE_RESOLVER = ShapeResolver.pipelineHead();

  @Deprecated
  // I suck, please remove
  public static List<BoundingBox> __INVALID__resolveBoxes__OnlyForBoxIntersectionChecks__(
    Player player, BoundingBox playerBoundingBox) {
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
    ExtendedBlockStateCache stateAccess = user.blockStates();
    World world = player.getWorld();
    int blockRemaining = COLLISION_CHECK_LIMIT;
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
                BlockShape blockShape = stateAccess.collisionShapeAt(x, y, z);
                Material material = stateAccess.typeAt(x, y, z);
                if (CollisionModifiers.isModified(material)) {
                  blockShape = CollisionModifiers.modified(
                    user, playerBoundingBox, material, x, y, z,
                    blockShape, CollisionOrigin.INTERSECTION_CHECK
                  );
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
    double radius = WorldBorders.sizeOfWorldBorderIn(world) / 2.0;
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

  public static boolean playerInImaginaryBlock(
    User user, World world, int posX, int posY, int posZ, Material type, int variant) {
    BlockShape boundingBoxes = SHAPE_RESOLVER.collisionShapeOf(world, user.player(), type, variant, posX, posY, posZ);
    if (CollisionModifiers.isModified(type)) {
      BlockShape customShape = CollisionModifiers.imaginaryBlockShape(type, user, posX, posY, posZ, variant);
      if (customShape != null) {
        boundingBoxes = customShape;
      }
    }
    if (boundingBoxes == null || boundingBoxes.isEmpty()) {
      return false;
    }
    BoundingBox playerBox = user.meta().movement().boundingBox();
    playerBox = playerBox.shrink(0.05); // hmm
    return boundingBoxes.intersectsWith(playerBox);
  }

  public static boolean nearSolidBlock(User user, BoundingBox boundingBox) {
    return rasterizedTypeSearch(user, boundingBox, material -> {
      return !MaterialMagic.isLavaOrWater(material) && material != Material.AIR;
    });
  }

  public static boolean rasterizedTypeSearch(
    User user, BoundingBox boundingBox, Material material
  ) {
    return rasterizedTypeSearch(user, boundingBox, material::equals);
  }

  public static boolean rasterizedTypeSearch(
    User user, BoundingBox boundingBox, Predicate<? super Material> typePredicate
  ) {
    return rasterizedSearch(
      boundingBox, blockPosition -> typePredicate.test(VolatileBlockAccess.typeAccess(user, blockPosition))
    );
  }

  public static boolean rasterizedTypeEnforcement(
    User user, BoundingBox boundingBox, Predicate<? super Material> typePredicate
  ) {
    return rasterizedEnforcement(
      boundingBox, blockPosition -> typePredicate.test(VolatileBlockAccess.typeAccess(user, blockPosition))
    );
  }

  public static boolean rasterizedLiquidSearch(
    User user, BoundingBox boundingBox, Predicate<? super Fluid> liquidPredicate
  ) {
    return rasterizedSearch(
      boundingBox, blockPosition -> liquidPredicate.test(Fluids.fluidAt(user, blockPosition))
    );
  }

  public static boolean rasterizedLiquidEnforcement(
    User user, BoundingBox boundingBox, Predicate<? super Fluid> liquidPredicate
  ) {
    return rasterizedEnforcement(
      boundingBox, blockPosition -> liquidPredicate.test(Fluids.fluidAt(user, blockPosition))
    );
  }

  public static boolean rasterizedLiquidPresentSearch(
    User user, BoundingBox boundingBox
  ) {
    return rasterizedSearch(
      boundingBox, blockPosition -> Fluids.fluidPresentAt(user, blockPosition)
    );
  }

  public static boolean rasterizedLiquidPresentEnforcement(
    User user, BoundingBox boundingBox
  ) {
    return rasterizedEnforcement(
      boundingBox, blockPosition -> Fluids.fluidPresentAt(user, blockPosition)
    );
  }

  public static boolean rasterizedSearch(
    BoundingBox boundingBox, Function<? super BlockPosition, Boolean> predicate
  ) {
    return collectRasterizedCollisions(
      boundingBox, predicate, Boolean::booleanValue,
      Collectors.reducing(false, Boolean::logicalOr)
    );
  }

  public static boolean rasterizedEnforcement(
    BoundingBox boundingBox, Function<? super BlockPosition, Boolean> predicate
  ) {
    return collectRasterizedCollisions(
      boundingBox, predicate, aBoolean -> !aBoolean,
      Collectors.reducing(true, Boolean::logicalAnd)
    );
  }

  public static <I, C, R> R collectRasterizedCollisions(
    BoundingBox boundingBox,
    Function<? super BlockPosition, ? extends I> input,
    Predicate<? super I> isFinal,
    Collector<I, C, R> collector
  ) {
    C container = collector.supplier().get();
    BiConsumer<C, I> accumulator = collector.accumulator();
    Function<C, R> finisher = collector.finisher();
    int minX = floor(boundingBox.minX);
    int minY = floor(boundingBox.minY);
    int minZ = floor(boundingBox.minZ);
    int maxX = floor(boundingBox.maxX);
    int maxY = floor(boundingBox.maxY);
    int maxZ = floor(boundingBox.maxZ);
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        for (int z = minZ; z <= maxZ; z++) {
          I apply = input.apply(new BlockPosition(x, y, z));
          accumulator.accept(container, apply);
          if (isFinal.test(apply)) {
            return finisher.apply(container);
          }
        }
      }
    }
    return finisher.apply(container);
  }
}

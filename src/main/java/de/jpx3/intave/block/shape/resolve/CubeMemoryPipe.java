package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.diagnostic.ShapeAccessFlowStudy;
import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Set;

final class CubeMemoryPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;
  private final CubeMemory collisionCubeMemory = new CubeMemory();
  private final CubeMemory outlineCubeMemory = new CubeMemory();

  public CubeMemoryPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
    this.preloadBlocks();
  }

  public void preloadBlocks() {
    for (Material type : Material.values()) {
      String typeName = type.name();
      if (typeName.contains("SLAB") /* can be doubled */) {
        collisionCubeMemory.rememberNonCubic(type);
        outlineCubeMemory.rememberNonCubic(type);
      }
    }
  }

  @Override
  public BlockShape collisionShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
    CubeMemory memory = collisionCubeMemory;
    if (memory.knownToBeCubic(type)) {
      ShapeAccessFlowStudy.incremDynamic();
      return BlockShapes.cubeAt(posX, posY, posZ);
    }
    BlockShape shape = forward.collisionShapeOf(world, player, type, variantIndex, posX, posY, posZ);
    if (!memory.knownToBeNonCubic(type) && isInLoadedChunk(world, posX, posZ)) {
      if (shape.isCubic()) {
        forward.downstreamTypeReset(type); // flush downstream type
        memory.rememberCubic(type);
      } else {
        memory.rememberNonCubic(type);
      }
    }
    return shape;
  }

  @Override
  public BlockShape outlineShapeOf(World world, Player player, Material type, int variantIndex, int posX, int posY, int posZ) {
    CubeMemory memory = outlineCubeMemory;
    if (memory.knownToBeCubic(type)) {
      ShapeAccessFlowStudy.incremDynamic();
      return BlockShapes.cubeAt(posX, posY, posZ);
    }
    BlockShape shape = forward.outlineShapeOf(world, player, type, variantIndex, posX, posY, posZ);
    if (!memory.knownToBeNonCubic(type) && isInLoadedChunk(world, posX, posZ)) {
      if (shape.isCubic()) {
        forward.downstreamTypeReset(type); // flush downstream type
        memory.rememberCubic(type);
      } else {
        memory.rememberNonCubic(type);
      }
    }
    return shape;
  }

  @Override
  public void downstreamTypeReset(Material type) {
    collisionCubeMemory.reset(type);
    outlineCubeMemory.reset(type);
    forward.downstreamTypeReset(type);
  }

  public static boolean isInLoadedChunk(World world, int x, int z) {
    if (Synchronizer.onFolia()) {
      // Another chunk-system read that is illegal off the owning region thread. It only
      // guards against learning a shape that was resolved from an unloaded chunk, and
      // the drill now reports such a failure by throwing instead of returning a guess,
      // so anything that got this far is trustworthy.
      return true;
    }
    return world.isChunkLoaded(x >> 4, z >> 4);
  }

  public static class CubeMemory {
    private final Set<Material> cubes = UnsafeCopyOnWriteEnumSet.of(Material.class);
    private final Set<Material> nonCubes = UnsafeCopyOnWriteEnumSet.of(Material.class);

    public void reset(Material type) {
      cubes.remove(type);
      nonCubes.remove(type);
    }

    public void rememberCubic(Material type) {
      cubes.add(type);
      nonCubes.remove(type);
    }

    public void rememberNonCubic(Material type) {
      cubes.remove(type);
      nonCubes.add(type);
    }

    public boolean knownToBeCubic(Material type) {
      return cubes.contains(type);
    }

    public boolean knownToBeNonCubic(Material type) {
      return nonCubes.contains(type);
    }
  }
}
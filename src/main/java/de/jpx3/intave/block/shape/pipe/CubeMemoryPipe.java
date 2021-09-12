package de.jpx3.intave.block.shape.pipe;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.CubeShape;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.shape.TrustingCopyOnWriteEnumSet;
import de.jpx3.intave.diagnostic.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.shade.BoundingBox;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

public final class CubeMemoryPipe implements ShapeResolverPipeline {
  private final ShapeResolverPipeline forward;
  private final Set<Material> solidMaterials = TrustingCopyOnWriteEnumSet.of(Material.class);
  private final Set<Material> otherMaterials = TrustingCopyOnWriteEnumSet.of(Material.class);

  public CubeMemoryPipe(ShapeResolverPipeline forward) {
    this.forward = forward;
    this.preloadBlocks();
  }

  public void preloadBlocks() {
    for (Material type : Material.values()) {
      String typeName = type.name();
      if (typeName.contains("SLAB") /* can be doubled */) {
        otherMaterials.add(type);
      }
    }
  }

  @Override
  public BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (solidMaterials.contains(type)) {
      BoundingBoxAccessFlowStudy.incremDynamic();
      return new CubeShape(posX, posY, posZ);
    } else if (otherMaterials.contains(type)) {
      return forward.resolve(world, player, type, blockState, posX, posY, posZ);
    }
    BlockShape shape = forward.resolve(world, player, type, blockState, posX, posY, posZ);
    if (isInLoadedChunk(world, posX, posZ)) {
      boolean solid = isCubic(shape.boundingBoxes(), posX, posY, posZ);
      if (solid) {
        downstreamTypeReset(type); // flush downstream type
      }
      (solid ? solidMaterials : otherMaterials).add(type);
    }
    return shape;
  }

  @Override
  public void downstreamTypeReset(Material type) {
    solidMaterials.remove(type);
    otherMaterials.remove(type);
    forward.downstreamTypeReset(type);
  }

  public static boolean isInLoadedChunk(World world, int x, int z) {
    return world.isChunkLoaded(x >> 4, z >> 4);
  }

  private boolean isCubic(List<BoundingBox> resolve, int posX, int posY, int posZ) {
    if (resolve.size() != 1) {
      return false;
    }
    BoundingBox theBox = resolve.get(0).offset(-posX, -posY, -posZ);
    return theBox.minX == 0 && theBox.minY == 0 && theBox.minZ == 0 &&
      theBox.maxX == 1 && theBox.maxY == 1 && theBox.maxZ == 1;
  }
}
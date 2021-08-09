package de.jpx3.intave.world.blockshape.resolver.pipeline;

import de.jpx3.intave.diagnostics.BoundingBoxAccessFlowStudy;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CubeMemoryPipe implements ResolverPipeline {
  private final ResolverPipeline forward;
  private final Set<Material> solidMaterials = new HashSet<>();
  private final Set<Material> otherMaterials = new HashSet<>();

  public CubeMemoryPipe(ResolverPipeline forward) {
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
  public List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ) {
    if (solidMaterials.contains(type)) {
      BoundingBoxAccessFlowStudy.incremDynamic();
      return Collections.singletonList(new WrappedAxisAlignedBB(posX, posY, posZ, posX + 1, posY + 1, posZ + 1));
    } else if (otherMaterials.contains(type)) {
      return forward.resolve(world, player, type, blockState, posX, posY, posZ);
    }
    List<WrappedAxisAlignedBB> resolve = forward.resolve(world, player, type, blockState, posX, posY, posZ);
    if (isInLoadedChunk(world, posX, posZ)) {
      boolean solid = isSolid(resolve, posX, posY, posZ);
      if (solid) {
        downstreamTypeReset(type); // flush downstream type save
      }
      (solid ? solidMaterials : otherMaterials).add(type);
    }
    return resolve;
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

  private boolean isSolid(List<WrappedAxisAlignedBB> resolve, int posX, int posY, int posZ) {
    if (resolve.size() != 1) {
      return false;
    }
    WrappedAxisAlignedBB theBox = resolve.get(0).offset(-posX, -posY, -posZ);
    return theBox.minX == 0 && theBox.minY == 0 && theBox.minZ == 0 &&
      theBox.maxX == 1 && theBox.maxY == 1 && theBox.maxZ == 1;
  }
}
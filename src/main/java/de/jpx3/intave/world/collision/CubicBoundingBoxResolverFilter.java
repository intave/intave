package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CubicBoundingBoxResolverFilter implements BoundingBoxResolver {
  private final BoundingBoxResolver forward;
  private final Set<Material> solidMaterials = new HashSet<>();
  private final Set<Material> otherMaterials = new HashSet<>();

  public CubicBoundingBoxResolverFilter(BoundingBoxResolver forward) {
    this.forward = forward;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, Material advanceType, int posX, int posY, int posZ) {
    if (solidMaterials.contains(advanceType)) {
      WrappedAxisAlignedBB defaultCubeBox = new WrappedAxisAlignedBB(
        posX, posY, posZ, posX + 1, posY + 1, posZ + 1
      );
      return Collections.singletonList(defaultCubeBox);
    } else if(otherMaterials.contains(advanceType)) {
      return forward.resolve(world, advanceType, posX, posY, posZ);
    }
    List<WrappedAxisAlignedBB> resolve = forward.resolve(world, advanceType, posX, posY, posZ);
    boolean solid = false;
    if(resolve.size() == 1) {
      WrappedAxisAlignedBB theBox = resolve.get(0).offset(-posX, -posY, -posZ);
      if(
        theBox.minX == 0 && theBox.minY == 0 && theBox.minZ == 0 &&
        theBox.maxX == 1 && theBox.maxY == 1 && theBox.maxZ == 1
      ) solid = true;
    }
    (solid ? solidMaterials : otherMaterials).add(advanceType);
    return resolve;
  }

  @Override
  public List<WrappedAxisAlignedBB> resolve(World world, int posX, int posY, int posZ, int typeId, int blockState) {
    return forward.resolve(world, posX, posY, posZ, typeId, blockState);
  }
}
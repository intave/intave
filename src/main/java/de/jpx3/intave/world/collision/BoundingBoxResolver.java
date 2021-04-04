package de.jpx3.intave.world.collision;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Collections;
import java.util.List;

public interface BoundingBoxResolver {
  List<WrappedAxisAlignedBB> resolve(World world, Material advanceType, int posX, int posY, int posZ);

  default List<WrappedAxisAlignedBB> resolve(World world, int posX, int posY, int posZ, Material type, int blockState) {
    return Collections.emptyList();
  }
}

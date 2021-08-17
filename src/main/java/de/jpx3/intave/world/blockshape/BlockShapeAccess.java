package de.jpx3.intave.world.blockshape;

import de.jpx3.intave.world.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public interface BlockShapeAccess {
  List<WrappedAxisAlignedBB> resolveBoxes(int chunkX, int chunkZ, int posX, int posY, int posZ);

  List<WrappedAxisAlignedBB> constructBlock(World world, int posX, int posY, int posZ, Material type, int blockState);

  Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ);

  int resolveData(int chunkX, int chunkZ, int posX, int posY, int posZ);
}

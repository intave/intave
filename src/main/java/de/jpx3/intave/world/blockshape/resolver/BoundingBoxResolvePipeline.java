package de.jpx3.intave.world.blockshape.resolver;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public interface BoundingBoxResolvePipeline {
  @Deprecated
  List<WrappedAxisAlignedBB> nativeResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ);

  List<WrappedAxisAlignedBB> customResolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ);

  default void flushTypeCache(Material type) {

  }
}

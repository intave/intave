package de.jpx3.intave.world.blockshape.resolver.pipeline;

import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

public interface ResolverPipeline {
  List<WrappedAxisAlignedBB> resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ);

  default void downstreamTypeReset(Material type) {}
}

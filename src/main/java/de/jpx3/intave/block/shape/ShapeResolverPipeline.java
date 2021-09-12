package de.jpx3.intave.block.shape;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface ShapeResolverPipeline {
  BlockShape resolve(World world, Player player, Material type, int blockState, int posX, int posY, int posZ);

  default void downstreamTypeReset(Material type) {}
}

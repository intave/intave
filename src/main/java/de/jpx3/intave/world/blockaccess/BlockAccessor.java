package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.BlockPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public interface BlockAccessor {
  Material typeOf(Block block);

  int variantOf(Block block);

  Object blockHandle(Block block);

  float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition);

  boolean replacementPlace(World world, Player player, BlockPosition blockPosition);
}

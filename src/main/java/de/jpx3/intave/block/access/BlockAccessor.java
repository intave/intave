package de.jpx3.intave.block.access;

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

  int variantIndexOf(Block block);

  Object nativeVariantOf(Block block);

  @Deprecated
  Object nativeVariantBy(int blockId);

  float blockDamage(Player player, ItemStack itemInHand, BlockPosition blockPosition);

  boolean replacementPlace(World world, Player player, BlockPosition blockPosition);
}

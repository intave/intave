package de.jpx3.intave.access;

import org.bukkit.World;
import org.bukkit.entity.Player;

public abstract class BlockPlacePermissionCheck {
  public abstract boolean hasPermission(Player player, World world, boolean mainHand, int blockX, int blockY, int blockZ, int typeId, byte data);
}

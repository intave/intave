package de.jpx3.intave.world.permission;

import de.jpx3.intave.access.BlockPlacePermissionCheck;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class AllowAllPlacePermissionResolver implements BlockPlacePermissionCheck {
  @Override
  public boolean hasPermission(Player player, World world, boolean mainHand, int blockX, int blockY, int blockZ, int enumDirection, int typeId, byte data) {
    return true;
  }
}

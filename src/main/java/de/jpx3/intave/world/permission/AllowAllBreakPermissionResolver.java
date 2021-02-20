package de.jpx3.intave.world.permission;

import de.jpx3.intave.access.BlockBreakPermissionCheck;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class AllowAllBreakPermissionResolver implements BlockBreakPermissionCheck {
  @Override
  public boolean hasPermission(Player player, Block block) {
    return true;
  }
}

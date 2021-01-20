package de.jpx3.intave.access;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface BlockBreakPermissionCheck {
  boolean hasPermission(Player player, Block block);

  default void open() {};

  default void close() {};
}

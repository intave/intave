package de.jpx3.intave.entity.datawatcher;

import org.bukkit.entity.Player;

public interface DataWatcherAccessor {
  void setDataWatcherFlag(Player player, int key, boolean flag);
  boolean getDataWatcherFlag(Player player, int key);
}
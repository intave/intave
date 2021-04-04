package de.jpx3.intave.tools;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class AccessHelper {
  public static long now() {
    return System.currentTimeMillis();
  }

  public static boolean isOnline(OfflinePlayer player) {
    return player != null && (player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != null);
  }
}
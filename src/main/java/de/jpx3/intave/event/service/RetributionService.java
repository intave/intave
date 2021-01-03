package de.jpx3.intave.event.service;

import de.jpx3.intave.tools.sync.Synchronizer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class RetributionService {


  public void markPlayer(Player detectedPlayer, int vl, String checkName, String details) {
    sendMessageToAdministrators(detectedPlayer, vl, checkName, details);
  }

  private void sendMessageToAdministrators(Player detectedPlayer, int vl, String checkName, String details) {
    Synchronizer.synchronizeDelayed(() -> {
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        if (onlinePlayer.isOp()) {
          String message = resolveFlagMessage(detectedPlayer, vl, checkName, details);
          onlinePlayer.sendMessage(message);
        }
      }
    }, 0);
  }

  private final static String PREFIX = "&8[&c&lIntave&8]&7 ";
  private final static String VERBOSE_FORMAT = "%s&7Verbose: &7%s %s and failed &c%s &7(+%s -> %s)";

  private String resolveFlagMessage(Player player, int vl, String checkName, String details) {
    String message = String.format(
      VERBOSE_FORMAT,
      PREFIX, player.getName(), details, checkName, vl, 0
    );
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
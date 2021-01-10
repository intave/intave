package de.jpx3.intave.event.service;

import de.jpx3.intave.tools.sync.Synchronizer;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class RetributionService {


  public boolean markPlayer(Player detectedPlayer, int vl, String checkName, String details) {
    sendMessageToAdministrators(detectedPlayer, vl, checkName, details);
    return true;
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
  private final static String VERBOSE_FORMAT = "%s&c%s &7%s &7(%s / +%s -> %s)";

  private String resolveFlagMessage(Player player, int vl, String checkName, String details) {
    User user = UserRepository.userOf(player);

    String message = String.format(
      VERBOSE_FORMAT,
      PREFIX, player.getName(), details, checkName.toLowerCase(Locale.ROOT), vl, 0
    );
    return ChatColor.translateAlternateColorCodes('&', message);
  }
}
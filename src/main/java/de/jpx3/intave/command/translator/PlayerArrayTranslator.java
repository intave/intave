package de.jpx3.intave.command.translator;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.TypeTranslator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PlayerArrayTranslator extends TypeTranslator<Player[]> {

  public PlayerArrayTranslator() {
    super(Player[].class);
  }

  @Override
  public Player[] resolve(CommandSender commandSender, String element, String forward) {
    String[] playerNames = forward.split(" ");
    List<Player> players = new ArrayList<>();
    for (String playerName : playerNames) {
      Player player = Bukkit.getPlayer(playerName);
      if(player == null || !player.isOnline()) {
        commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Invalid argument \""+playerName+"\": Unable to locate player");
        return null;
      }
      players.add(player);
    }
    return players.toArray(new Player[0]);
  }

  @Override
  public List<String> settingConstrains(CommandSender commandSender) {
    return null;
  }
}

package de.jpx3.intave.command.translator;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.TypeTranslator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PlayerTranslator extends TypeTranslator<Player> {
  public PlayerTranslator() {
    super(Player.class);
  }

  @Override
  public Player resolve(CommandSender commandSender, String element, String forward) {
    Player player = Bukkit.getPlayer(element);
    if(player == null || !player.isOnline()) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Invalid argument \""+element+"\": Unable to locate player");
      return null;
    }
    return player;
  }

  @Override
  public List<String> settingConstrains(CommandSender commandSender) {
    List<String> playerNames = new ArrayList<>();
    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
      boolean add = true;
      if(commandSender instanceof Player) {
        add = ((Player) commandSender).canSee(onlinePlayer);
      }
      if(add) {
        playerNames.add(onlinePlayer.getName());
      }
    }
    return playerNames;
  }
}

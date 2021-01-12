package de.jpx3.intave.command.translator;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.command.TypeTranslator;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class IntegerTranslator extends TypeTranslator<Integer> {

  public IntegerTranslator() {
    super(Integer.class);
  }

  @Override
  public Integer resolve(CommandSender commandSender, String element, String forward) {
    try {
      return Integer.parseInt(element);
    } catch (Exception exception) {
      commandSender.sendMessage(IntavePlugin.prefix() + ChatColor.RED + "Invalid argument " + element + ": Must be valid integer");
      return null;
    }
  }

  @Override
  public List<String> settingConstrains(CommandSender commandSender) {
    return null;
  }
}

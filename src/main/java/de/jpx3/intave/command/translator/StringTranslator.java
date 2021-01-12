package de.jpx3.intave.command.translator;

import de.jpx3.intave.command.TypeTranslator;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class StringTranslator extends TypeTranslator<String> {
  public StringTranslator() {
    super(String.class);
  }

  @Override
  public String resolve(CommandSender commandSender, String element, String forward) {
    return element;
  }

  @Override
  public List<String> settingConstrains(CommandSender commandSender) {
    return null;
  }
}

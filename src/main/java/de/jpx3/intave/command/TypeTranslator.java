package de.jpx3.intave.command;

import org.bukkit.command.CommandSender;

import java.util.List;

public abstract class TypeTranslator<T> {
  private final Class<T> type;

  public TypeTranslator(Class<T> type) {
    this.type = type;
  }

  public abstract T resolve(CommandSender commandSender, String element, String forward);

  public abstract List<String> settingConstrains(CommandSender commandSender);

  public Class<T> type() {
    return type;
  }
}

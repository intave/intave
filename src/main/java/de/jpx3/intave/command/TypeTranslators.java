package de.jpx3.intave.command;

import com.google.common.collect.Lists;
import de.jpx3.intave.command.translator.*;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TypeTranslators {
  private final static Map<Class<?>, TypeTranslator<?>> typeTranslatorMap = new HashMap<>();

  static {
    add(IntegerTranslator.class);
    add(StringArrayTranslator.class);
    add(StringTranslator.class);
    add(PlayerTranslator.class);
    add(PlayerArrayTranslator.class);
  }

  private static void add(Class<? extends TypeTranslator<?>> typeTranslatorClass) {
    TypeTranslator<?> typeTranslator;
    try {
      typeTranslator = typeTranslatorClass.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
    typeTranslatorMap.put(typeTranslator.type(), typeTranslator);
  }

  public static Object tryTranslate(CommandSender player, Class<?> type, String element, String forward) {
  /*  while (type.isArray()) {
      type = type.getComponentType();
    }
*/
    TypeTranslator<?> typeTranslator = typeTranslatorMap.get(type);
    if(typeTranslator == null) {
      return ("Invalid type: " + type);
    }
    return typeTranslator.resolve(player, element, forward);
  }

  public static List<String> findTabCompletes(CommandSender player, Class<?> type, String element, String forward) {
    TypeTranslator<?> typeTranslator = typeTranslatorMap.get(type);
    return typeTranslator == null ? Collections.emptyList() : typeTranslator.settingConstrains(player);
  }
}

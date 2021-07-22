package de.jpx3.intave.command;

import de.jpx3.intave.command.translator.*;
import org.bukkit.command.CommandSender;

import java.util.*;
import java.util.stream.Collectors;

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
    if (type.isEnum()) {
      Enum<?>[] enumConstants = (Enum<?>[]) type.getEnumConstants();
      for (Enum<?> enumConstant : enumConstants) {
        if (enumConstant.name().equalsIgnoreCase(element) || niceifyEnumName(enumConstant.name()).equalsIgnoreCase(element)) {
          return enumConstant;
        }
      }
      String types = Arrays.stream(enumConstants).map(enumConstant -> niceifyEnumName(enumConstant.name())).collect(Collectors.joining(", "));
      int indexOfLastComma = types.lastIndexOf(", ");
      types = types.substring(0, indexOfLastComma - 1) + " or " + types.substring(indexOfLastComma);
      return "Unknown type \"" + type + "\": Expected " + types;
    }
    TypeTranslator<?> typeTranslator = typeTranslatorMap.get(type);
    if (typeTranslator == null) {
      return "Invalid type: " + type;
    }
    return typeTranslator.resolve(player, element, forward);
  }

  public static List<String> findTabCompletes(CommandSender player, Class<?> type, String element, String forward) {
    if (type.isEnum()) {
      Enum<?>[] enumConstants = (Enum<?>[]) type.getEnumConstants();
      List<String> strings = new ArrayList<>();
      for (Enum<?> enumConstant : enumConstants) {
        strings.add(niceifyEnumName(enumConstant.name()));
      }
      return strings;
    }
    TypeTranslator<?> typeTranslator = typeTranslatorMap.get(type);
    return typeTranslator == null ? Collections.emptyList() : typeTranslator.settingConstrains(player);
  }

  private static String niceifyEnumName(String input) {
    return input.toLowerCase(Locale.ROOT).replace("_", "");
  }
}

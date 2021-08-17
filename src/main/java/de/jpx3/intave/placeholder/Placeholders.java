package de.jpx3.intave.placeholder;

import java.util.List;
import java.util.Map;

public final class Placeholders {
  public static final PluginContext PLUGIN_CONTEXT = new PluginContext();
  public static final ServerContext SERVER_CONTEXT = new ServerContext();

  private Placeholders() {
    throw new IllegalStateException();
  }

  public static String replacePlaceholders(
    String initialString,
    List<PlaceholderContext> context
  ) {
    return replacePlaceholders(initialString, context.toArray(new PlaceholderContext[0]));
  }

  public static String replacePlaceholders(
    String initialString,
    PlaceholderContext... context
  ) {
    return replacePlaceholders(initialString, PlaceholderCombiner.combine(context).replacements());
  }

  private static String replacePlaceholders(
    String initialString,
    Map<String, String> placeholderToReplacementMap
  ) {
    if (initialString == null || initialString.isEmpty()) {
      return "";
    }
    for (
      Map.Entry<String, String> stringStringEntry : placeholderToReplacementMap.entrySet()
    ) {
      String replacement = stringStringEntry.getValue();
      initialString = initialString.replaceAll(
        "\\{" + stringStringEntry.getKey() + "}",
        replacement
      );
      initialString = initialString.replaceAll(
        "%" + stringStringEntry.getKey() + "%",
        replacement
      );
    }
    return initialString;
  }
}

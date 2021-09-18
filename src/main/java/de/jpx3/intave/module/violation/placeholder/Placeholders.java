package de.jpx3.intave.module.violation.placeholder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

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
    return replacePlaceholders(initialString, combine(context).replacements());
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

  public static PlaceholderContext combine(PlaceholderContext... contexts) {
    Map<String, String> globalContext = Maps.newHashMap();
    for (PlaceholderContext context : contexts) {
      if (context == null) continue;
      globalContext.putAll(context.replacements());
    }
    Map<String, String> immutableContextMap = ImmutableMap.copyOf(globalContext);
    return new PlaceholderContext() {
      @Override
      public Map<String, String> replacements() {
        return immutableContextMap;
      }
    };
  }
}

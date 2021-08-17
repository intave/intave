package de.jpx3.intave.placeholder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

public final class PlaceholderCombiner {
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

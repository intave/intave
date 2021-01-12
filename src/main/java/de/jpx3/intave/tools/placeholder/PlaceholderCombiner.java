package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

public final class PlaceholderCombiner {
  public static PlaceholderContext combine(PlaceholderContext... contexts) {
    Map<String, String> initialContext = Maps.newHashMap();
    for (PlaceholderContext context : contexts) {
      if(context == null) continue;
      initialContext.putAll(context.replacements());
    }
    Map<String, String> immutableContextMap = ImmutableMap.copyOf(initialContext);
    return new PlaceholderContext() {
      @Override
      public Map<String, String> replacements() {
        return immutableContextMap;
      }
    };
  }
}

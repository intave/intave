package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.IntavePlugin;

import java.util.Map;

public final class PluginContext extends PlaceholderContext {
  @Override
  public Map<String, String> replacements() {
    String prefix = IntavePlugin.prefix();
    return ImmutableMap.of("prefix", prefix);
  }
}

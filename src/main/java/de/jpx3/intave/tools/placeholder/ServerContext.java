package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class ServerContext extends PlaceholderContext {
  @Override
  public Map<String, String> replacements() {
    return ImmutableMap.of(
      "tps", "20.0"
    );
  }
}

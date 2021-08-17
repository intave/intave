package de.jpx3.intave.placeholder;

import com.google.common.collect.ImmutableMap;
import de.jpx3.intave.tools.TPSArrayAccessor;

import java.util.Map;

public final class ServerContext extends PlaceholderContext {
  @Override
  public Map<String, String> replacements() {
    return ImmutableMap.of(
      "tps", TPSArrayAccessor.stringFormattedTick()
    );
  }
}

package de.jpx3.intave.tools.placeholder;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class TextContext extends PlaceholderContext{
  private final String text;

  public TextContext(String text) {
    this.text = text;
  }

  @Override
  public Map<String, String> replacements() {
    return ImmutableMap.of("text", text);
  }
}

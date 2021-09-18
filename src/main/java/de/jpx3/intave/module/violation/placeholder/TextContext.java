package de.jpx3.intave.module.violation.placeholder;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

public final class TextContext implements PlaceholderContext{
  private final String text;

  public TextContext(String text) {
    this.text = text;
  }

  @Override
  public Map<String, String> replacements() {
    return ImmutableMap.of("text", text);
  }
}

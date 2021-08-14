package de.jpx3.intave.access.check;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Mitigation strategy
 */
public enum MitigationStrategy {
  AGGRESSIVE("AGGRESSIVE"),
  CAREFUL("CAREFUL"),
  LENIENT("LENIENT"),
  SILENT("SILENT"),

  NOT_SUPPORTED("");

  private final static Map<String, MitigationStrategy> BY_NAME = new HashMap<>();
  static {
    Arrays.stream(values()).forEach(value -> BY_NAME.put(value.name, value));
  }

  public static MitigationStrategy byName(String name) {
    MitigationStrategy mitigationStrategy = BY_NAME.get(name.toUpperCase(Locale.ROOT));
    if (mitigationStrategy == null) {
      mitigationStrategy = MitigationStrategy.NOT_SUPPORTED;
    }
    return mitigationStrategy;
  }

  private final String name;

  MitigationStrategy(String name) {
    this.name = name;
  }
}

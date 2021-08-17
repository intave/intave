package de.jpx3.intave.tools.version;

import java.util.concurrent.TimeUnit;

public final class DurationTranslator {
  public static String translateDuration(long duration) {
    if (duration <= 0) {
      return "invalid";
    }
    int hours = (int) (duration / (1000 * 60 * 60));
    int days = hours / 24;
    hours = hours % 24;
    String firstType = stringifyType(TimeUnit.DAYS, days);
    String secondType = stringifyType(TimeUnit.HOURS, hours);
    if (secondType.isEmpty()) {
      secondType = "0 hours";
    }
    String output;
    if (days >= 7) {
      output = firstType;
    } else {
      output = firstType + (firstType.isEmpty() ? "" : " and ") + secondType;
    }
    if (output.trim().isEmpty()) {
      output = "a few hours";
    }
    return output;
  }

  private static String stringifyType(TimeUnit unit, long conv) {
    if (conv == 0) {
      return "";
    }
    String name = unit.name().toLowerCase();
    return (conv == 1 ? "one" : conv) + " " + name.substring(0, name.length() - (conv == 1 ? 1 : 0));
  }
}

package de.jpx3.intave.tools;

import java.util.concurrent.TimeUnit;

public final class DurationTranslator {

  public static String translateDuration(long duration) {
    if(duration <= 0) {
      return "invalid";
    }
    int hours = (int) (duration / (1000 * 60 * 60));
    int days = hours / 24;
    hours = hours % 24;
    String firstType = stringifyType(TimeUnit.DAYS, days);
    String secondType = stringifyType(TimeUnit.HOURS, hours);
    return firstType + (firstType.isEmpty() ? "" : " and ") + secondType;
  }

  private static String stringifyType(TimeUnit unit, long conv) {
    String name = unit.name().toLowerCase();
    return (conv == 1 ? "one" : conv) + " " + name.substring(0, name.length() - (conv == 1 ? 1 : 0));
  }
}

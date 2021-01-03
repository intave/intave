package de.jpx3.intave.detect.checks.combat.heuristics;

import de.jpx3.intave.tools.AccessHelper;

import java.util.concurrent.TimeUnit;

public class Anomaly {
  private final static long ANOMALY_EXPIRE_DURATION = TimeUnit.MINUTES.toMillis(5);

  private final long added;
  private final String description;
  private final Confidence confidence;
  private final int options;

  public Anomaly(
    String description,
    Confidence confidence,
    int options) {
    this.added = AccessHelper.now();
    this.description = description;
    this.confidence = confidence;
    this.options = options;
  }

  public long timestamp() {
    return added;
  }

  public String description() {
    return description;
  }

  public Confidence confidence() {
    return confidence;
  }

  public boolean expired() {
    return AccessHelper.now() - added > ANOMALY_EXPIRE_DURATION;
  }

  public static class AnomalyOption {
    public final static int LIMIT_1 = 1;
    public final static int LIMIT_2 = 1 << 1;
    public final static int LIMIT_3 = 1 << 2;
    public final static int LIMIT_4 = 1 << 3;
    public final static int SUGGEST_MINING = 1 << 4;
    public final static int REQUIRES_HEAVY_COMBAT = 1 << 5;
    public final static int DELAY_16s = 1 << 6;
    public final static int DELAY_32s = 1 << 7;
    public final static int DELAY_64s = 1 << 8;
    public final static int DELAY_128s = 1 << 9;

    public static boolean matches(int optionInt, int option) {
      return (optionInt & option) > 0;
    }

    public static int delayInSeconds(int optionInt) {
      return (optionInt & (DELAY_16s | DELAY_32s | DELAY_64s | DELAY_128s)) >> 2;
    }
  }
}

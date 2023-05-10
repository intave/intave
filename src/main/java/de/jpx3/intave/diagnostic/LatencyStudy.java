package de.jpx3.intave.diagnostic;

import java.util.concurrent.TimeUnit;

public final class LatencyStudy {
  //  private final static Map<Short, AtomicLong> hitDelays = new ConcurrentHashMap<>();
  private static long hitDelayCount;
  private static long hitDelayNum;

  private static long transPingCount;
  private static long transPingNum;

  public static void enterHit(short tickLatency) {
//    hitDelays.computeIfAbsent(tickLatency, x -> new AtomicLong(0L)).incrementAndGet();
    hitDelayNum += tickLatency;
    hitDelayCount++;

    if (hitDelayNum > Integer.MAX_VALUE / 2) {
      hitDelayNum /= 2;
      hitDelayCount /= 2;
    }
  }

  public static double attackLatency() {
    return hitDelayNum == 0 ? 0 : (double) hitDelayNum / hitDelayCount;
  }

  public static void receivedTransactionAfter(long milliseconds) {
    transPingNum += Math.min(milliseconds, 1000);
    transPingCount++;

    if (transPingNum > Integer.MAX_VALUE / 2) {
      transPingNum /= 2;
      transPingCount /= 2;
    }
  }

  public static long pingAverage() {
    return transPingNum == 0 ? 0 : transPingNum / transPingCount;
  }

  private static double cachedAverage;
  private static long lastCachedAverageReset;

  private static final long CACHE_EXPIRY = TimeUnit.SECONDS.toMillis(5);

  public static double cachedAverage() {
    if (System.currentTimeMillis() - lastCachedAverageReset > CACHE_EXPIRY) {
      cachedAverage = attackLatency();
      lastCachedAverageReset = System.currentTimeMillis();
    }
    return cachedAverage;
  }
}

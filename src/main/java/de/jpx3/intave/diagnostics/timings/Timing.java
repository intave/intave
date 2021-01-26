package de.jpx3.intave.diagnostics.timings;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public class Timing implements Cloneable, Comparable<Timing> {
  public static final Timing CHECK_PHYSICS_PROCESS = Timing.of("Check Physics Process");
  public static final Timing CHECK_PHYSICS_EVALUATION = Timing.of("Check Physics Evaluation");

  private final String timingName;
  private final TimingData totalTimingData = new TimingData();

//  private Lock lock = new ReentrantLock();
  private final ThreadLocal<Long> lastStart = ThreadLocal.withInitial(() -> 0L);

  private Timing(String timingName) {
    this.timingName = timingName;
  }

  public void start() {
    // start from sync start
    lastStart.set(now());
  }

  public void stop() {
    // end from before sync
    long currentTimestamp = now();
    totalTimingData.addTime(currentTimestamp - lastStart.get());
    totalTimingData.increaseCallCount();
  }

  public String getTimingName() {
    return timingName;
  }

  public long getTotalDurationNanos() {
    return totalTimingData.getTotalDuration();
  }

  public double totalDurationMillis() {
    return getTotalDurationNanos() / 1000000d;
  }

  public long getRecordedCalls() {
    return totalTimingData.getCalls();
  }

  public double getAverageCallDurationInNanos() {
    return getTotalDurationNanos() / Math.max(1d, getRecordedCalls());
  }

  public double getAverageCallDurationInMillis() {
    return (totalDurationMillis()) / (double) Math.max(1, getRecordedCalls());
  }

  public double getDurationComparedToTick() {
    return getAverageCallDurationInMillis() / 50;
  }

  @Override
  public int compareTo(Timing o) {
    return Long.compare(o.getTotalDurationNanos(), getTotalDurationNanos());
  }

  @Override
  public Timing clone() {
    try {
      return (Timing) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new IllegalStateException(e);
    }
  }

  private static long now() {
    return System.nanoTime();
  }

  static Timing of(String name) {
    Timing timing = new Timing(name);
    Timings.addTiming(timing);
    return timing;
  }
}

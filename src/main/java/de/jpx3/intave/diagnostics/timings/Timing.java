package de.jpx3.intave.diagnostics.timings;

import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public class Timing implements Cloneable, Comparable<Timing> {

  private final String timingName;
  private final String parentName;
  private TimingType timingType = TimingType.BASE;
  private final TimingData totalTimingData = new TimingData();

//  private Lock lock = new ReentrantLock();
  private final ThreadLocal<Long> lastStart = ThreadLocal.withInitial(() -> 0L);

  private Timing(String timingName, String parentName) {
    this.timingName = timingName;
    this.parentName = parentName;
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

  public String parentName() {
    return parentName;
  }

  public Timing parent() {
    return parentName == null ? null : Timings.lookupTimingByName(parentName);
  }

  public String name() {
    return timingName;
  }

  public String coloredName() {
    String name = name();
    List<String> path = Arrays.stream(name.split("/")).collect(Collectors.toList());
    for (int i = 0, pathSize = path.size(); i < pathSize; i++) {
      String pathElement = path.get(i);
      ChatColor correspCC = Timings.COLOR_CODE_NAMESPACE.get(pathElement);
      if(correspCC != null) {
        pathElement = correspCC + pathElement + ChatColor.WHITE;
      }
      path.set(i, pathElement);
    }
    String outputString = path.stream().map(s -> s + "/").collect(Collectors.joining());
    return outputString.substring(0, outputString.length() - 1);
  }

  public void specifyAsBukkitEventTiming() {
    timingType = TimingType.BUKKIT_EVENT;
  }

  public boolean isBukkitEventTiming() {
    return timingType == TimingType.BUKKIT_EVENT;
  }

  public void specifyAsPacketEventTiming() {
    timingType = TimingType.PACKET_EVENT;
  }

  public boolean isPacketEventTiming() {
    return timingType == TimingType.PACKET_EVENT;
  }

  public long getTotalDurationNanos() {
    return totalTimingData.getTotalDuration();
  }

  public double totalDurationMillis() {
    return getTotalDurationNanos() / 1000000d;
  }

  public long recordedCalls() {
    return totalTimingData.getCalls();
  }

  public double getAverageCallDurationInNanos() {
    return getTotalDurationNanos() / Math.max(1d, recordedCalls());
  }

  public double averageCallDurationInMillis() {
    return (totalDurationMillis()) / (double) Math.max(1, recordedCalls());
  }

  public double getDurationComparedToTick() {
    return averageCallDurationInMillis() / 50;
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
    Timing timing = new Timing(name, null);
    Timings.addTiming(timing);
    return timing;
  }

  static Timing of(String name, String parentName) {
    Timing timing = new Timing(name, parentName);
    Timings.addTiming(timing);
    return timing;
  }

  public enum TimingType {
    BASE,
    BUKKIT_EVENT,
    PACKET_EVENT
  }
}

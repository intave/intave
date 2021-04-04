package de.jpx3.intave.diagnostics.timings;

import com.google.common.collect.Maps;
import org.bukkit.event.Event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public class Timings {
  private static final List<Timing> timingPool = new CopyOnWriteArrayList<>();
  private static final Map<String, Timing> eventTimings = Maps.newConcurrentMap();
  private final static Map<Class<?>, String> classNameCache = Maps.newConcurrentMap();

  public static final Timing CHECK_PHYSICS_PROC_TOT = Timing.of("Check/Physics/ProcTot");
  public static final Timing CHECK_PHYSICS_PROC_BIA = Timing.of("Check/Physics/ProcBia");
  public static final Timing CHECK_PHYSICS_PROC_ITR = Timing.of("Check/Physics/ProcItr");
  public static final Timing CHECK_PHYSICS_EVAL = Timing.of("Check/Physics/Eval");

  public static final Timing EXE_BACKGROUND = Timing.of("Exe/Background");
  public static final Timing EXE_SERVER = Timing.of("Exe/Server");

  public static void addTiming(Timing timing) {
    timingPool.add(timing);
  }

  public static Timing eventTimingOf(Event event) {
    String eventName = classNameCache.computeIfAbsent(event.getClass(), eventClass -> event.getEventName());
    return eventTimings.computeIfAbsent(eventName, x -> Timing.of("Event/" + eventName));
  }

  public static List<Timing> timingPool() {
    return timingPool;
  }
}

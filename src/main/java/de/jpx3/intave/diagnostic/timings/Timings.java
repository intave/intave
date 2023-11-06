package de.jpx3.intave.diagnostic.timings;

import com.comphenix.protocol.PacketType;
import com.google.common.collect.Maps;
import org.bukkit.ChatColor;
import org.bukkit.event.Event;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Class generated using IntelliJ IDEA
 * Any distribution is strictly prohibited.
 * Copyright Richard Strunk 2019
 */

public class Timings {
  private static final List<Timing> timingPool = new CopyOnWriteArrayList<>();
  private static final Map<String, Timing> eventTimings = Maps.newConcurrentMap();
  private static final Map<String, Timing> packetTimings = Maps.newConcurrentMap();
  private static final Map<Class<?>, String> classNameCache = Maps.newConcurrentMap();

  public static final Timing CHECK_PHYSICS_PROC_TOT = Timing.of("Check/Physics/Proc", "Exe/Netty");
  public static final Timing CHECK_PHYSICS_PROC_BIA = Timing.of("Check/Physics/Proc/Bia", "Check/Physics/Proc");
  public static final Timing CHECK_PHYSICS_PROC_PRED_BIA = Timing.of("Check/Physics/Proc/Bia/Pred", "Check/Physics/Proc/Bia");
  public static final Timing CHECK_PHYSICS_PROC_LK_BIA = Timing.of("Check/Physics/Proc/Bia/lK", "Check/Physics/Proc/Bia");
  public static final Timing CHECK_PHYSICS_PROC_ITR = Timing.of("Check/Physics/Proc/Itr", "Check/Physics/Proc/Tot");
  public static final Timing CHECK_PHYSICS_EVAL = Timing.of("Check/Physics/Eval", "Check/Physics/ProcTot");

  public static final Timing SERVICE_TYPE_LOOKUP = Timing.of("Service/Lookup/Type");

  public static final Timing SERVICE_RAYTRACER_ENTITY = Timing.of("Service/Raytracer/Entity", "Exe/Netty");
  public static final Timing SERVICE_RAYTRACER_BLOCK = Timing.of("Service/Raytracer/Block", "Exe/Netty");

  public static final Timing EXE_BACKGROUND_PRIMARY = Timing.of("Exe/Background/Primary");
  public static final Timing EXE_BACKGROUND_SECONDARY = Timing.of("Exe/Background/Secondary");
  public static final Timing EXE_BACKGROUND_TERTIARY = Timing.of("Exe/Background/Tertiary");
  public static final Timing EXE_SERVER = Timing.of("Exe/Server");
  public static final Timing EXE_NETTY = Timing.of("Exe/Netty");

  public static final Map<String, ChatColor> COLOR_CODE_NAMESPACE = new HashMap<String, ChatColor>() {
      /*<init>*/ {
          put("Check", ChatColor.RED);
          put("Service", ChatColor.YELLOW);
          put("Exe", ChatColor.GRAY);
          put("Event", ChatColor.GOLD);
          put("Packet", ChatColor.DARK_PURPLE);
      }
  };

  public static void addTiming(Timing timing) {
    timingPool.add(timing);
  }

  public static Timing lookupTimingByName(String name) {
    return timingPool.stream().filter(timing -> timing.name().equalsIgnoreCase(name)).findFirst().orElse(null);
  }

  public static Timing eventTimingOf(Event event) {
    String eventName = classNameCache.computeIfAbsent(event.getClass(), eventClass -> event.getClass().getSimpleName());
    return eventTimings.computeIfAbsent(eventName, x -> {
      Timing timing = Timing.of("Event/" + x);
      timing.specifyAsBukkitEventTiming();
      return timing;
    });
  }

  public static Timing packetTimingOf(PacketType type) {
    String packetTypeName = type.name();
    return packetTimings.computeIfAbsent(packetTypeName, x -> {
      String name = !x.contains("_") ? firstUpper(x) : Arrays.stream(x.split("_")).map(Timings::firstUpper).collect(Collectors.joining());
      Timing timing = Timing.of("Packet/" + name);
      timing.specifyAsPacketEventTiming();
      return timing;
    });
  }

  public static String firstUpper(String string) {
    return string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
  }

  public static List<Timing> timingPool() {
    return timingPool;
  }
}

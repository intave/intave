package de.jpx3.intave.tools;

import de.jpx3.intave.agent.IntaveAgentAccessor;
import de.jpx3.intave.executor.BackgroundExecutor;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Consumer;

public final class MemoryWatchdog {
  public static Map<String, Object> watchedObjects = new WeakHashMap<>();

  public static <T> T watch(String name, T input) {
    if (supported()) {
      watchedObjects.put(name, input);
    }
    return input;
  }

  public static void memoryUsage(Consumer<Map<String, Long>> lazyReturn) {
    if (!supported()) {
      return;
    }
    BackgroundExecutor.execute(() -> {
      Map<String, Long> memoryUsage = new HashMap<>();
      Set<Object> identifiedObjects = new HashSet<>();
      watchedObjects.forEach((key, value) -> memoryUsage.put(key, memoryUsageOf(value, identifiedObjects)));
      identifiedObjects.clear();
      lazyReturn.accept(memoryUsage);
    });
  }

  public static long memoryTraceOf(Object object, Map<String, Long> trace, Collection<Object> identifiedObjects) {
    String className = object.getClass().getName();
    if (identifiedObjects.contains(object) || className.contains("org.bukkit") || className.contains("craftbukkit") || className.contains("net.minecraft")) {
      return 0;
    }
    identifiedObjects.add(object);
    long memoryUsage = IntaveAgentAccessor.instrumentation().getObjectSize(object);
//    if (object instanceof Map) {
//      for (Object o : ((Map<?, ?>) object).keySet()) {
//        memoryUsage += memoryTraceOf(o, trace, identifiedObjects);
//      }
//      for (Object value : ((Map<?, ?>) object).values()) {
//        memoryUsage += memoryTraceOf(value, trace, identifiedObjects);
//      }
//    }
//    if (object instanceof Iterable) {
//      Iterable<?> iterable = (Iterable<?>) object;
//      for (Object o : iterable) {
//        memoryUsage += memoryTraceOf(o, trace, identifiedObjects);
//      }
//    }
    for (Field declaredField : object.getClass().getDeclaredFields()) {
      try {
        if (!declaredField.isAccessible()) {
          declaredField.setAccessible(true);
        }
        memoryUsage += memoryTraceOf(declaredField.get(object), trace, identifiedObjects);
      } catch (Exception exception) {
      }
    }
    trace.put(className, memoryUsage);
    return memoryUsage;
  }

  public static long memoryUsageOf(Object object, Collection<Object> identifiedObjects) {
    String className = object.getClass().getName();
    if (identifiedObjects.contains(object) || className.contains("org.bukkit") || className.contains("craftbukkit") || className.contains("net.minecraft")) {
      return 0;
    }
    identifiedObjects.add(object);
    long memoryUsage = IntaveAgentAccessor.instrumentation().getObjectSize(object);
    if (object instanceof Map) {
      for (Object o : ((Map<?, ?>) object).keySet()) {
        memoryUsage += memoryUsageOf(o, identifiedObjects);
      }
      for (Object value : ((Map<?, ?>) object).values()) {
        memoryUsage += memoryUsageOf(value, identifiedObjects);
      }
    }
    if (object instanceof Iterable) {
      Iterable<?> iterable = (Iterable<?>) object;
      for (Object o : iterable) {
        memoryUsage += memoryUsageOf(o, identifiedObjects);
      }
    }
    for (Field declaredField : object.getClass().getDeclaredFields()) {
      try {
        declaredField.setAccessible(true);
        memoryUsage += memoryUsageOf(declaredField.get(object), identifiedObjects);
      } catch (Exception exception) {
//        exception.printStackTrace();
      }
    }
    return memoryUsage;
  }

  public static boolean supported() {
    return IntaveAgentAccessor.agentAvailable();
  }
}

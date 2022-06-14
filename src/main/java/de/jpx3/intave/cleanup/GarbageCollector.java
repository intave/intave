package de.jpx3.intave.cleanup;

import com.google.common.collect.Lists;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class GarbageCollector {
  private static final List<Reference<Map<?, ?>>> boundMaps = Lists.newCopyOnWriteArrayList();
  private static final List<Reference<List<?>>> boundLists = Lists.newCopyOnWriteArrayList();
  private static final List<Reference<Set<?>>> boundSets = Lists.newCopyOnWriteArrayList();

  private GarbageCollector() {
    throw new UnsupportedOperationException();
  }

  // class loading
  public static void setup() {
    ShutdownTasks.add(GarbageCollector::die);
  }

  public static <K, V> Map<K, V> watch(Map<K, V> initialMap) {
    boundMaps.add(new WeakReference<>(initialMap));
    return initialMap;
  }

  public static <T> List<T> watch(List<T> initialList) {
    boundLists.add(new WeakReference<>(initialList));
    return initialList;
  }

  public static <T> Set<T> watch(Set<T> initialSet) {
    boundSets.add(new WeakReference<>(initialSet));
    return initialSet;
  }

  public static <K> void clear(K key) {
    boundMaps.forEach(reference -> {
      Map<?, ?> map;
      if ((map = reference.get()) != null) {
        map.remove(key);
      }
    });
    boundLists.forEach(reference -> {
      List<?> list;
      if ((list = reference.get()) != null) {
        list.remove(key);
      }
    });
    boundSets.forEach(reference -> {
      Set<?> set;
      if ((set = reference.get()) != null) {
        set.remove(key);
      }
    });
  }

  public static void clearIf(Predicate<Object> check) {
    boundMaps.forEach(reference -> {
      Map<?, ?> map = reference.get();
      if (map != null) {
        map.entrySet().removeIf(entry -> check.test(entry.getKey()));
      }
    });
    boundLists.forEach(reference -> {
      List<?> list = reference.get();
      if (list != null) {
        list.removeIf(check);
      }
    });
    boundSets.forEach(reference -> {
      Set<?> set = reference.get();
      if (set != null) {
        set.removeIf(check);
      }
    });
  }

  public static void die() {
    boundMaps.clear();
    boundLists.clear();
    boundSets.clear();
  }
}

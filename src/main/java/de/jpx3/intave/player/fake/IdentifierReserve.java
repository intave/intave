package de.jpx3.intave.player.fake;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.executor.Synchronizer;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.IntStream;

/**
 * Entity Ids can only be aquired sync, but we need our ids async
 * So we reserve us a bunch of ids so we can use them later
 */
public final class IdentifierReserve {
  private final static Field ENTITY_COUNT_FIELD;
  private final static int REQUIRED_ID_POOL_SIZE = 25;

  static {
    try {
      Field entityCountField = Lookup.serverClass("Entity").getDeclaredField("entityCount");
      ENTITY_COUNT_FIELD = ensureAccessibility(entityCountField);
    } catch (NoSuchFieldException e) {
      throw new IntaveInternalException(e);
    }
  }

  private static Field ensureAccessibility(Field field) {
    if (!field.isAccessible()) {
      field.setAccessible(true);
    }
    return field;
  }

  private final static Queue<Integer> availableIds = new ConcurrentLinkedDeque<>();

  public static void setup() {
    refreshIfRequired();
  }

  public static int acquireNew() {
    refreshIfRequired();
    Integer poll = availableIds.poll();
    return poll != null ? poll : reserveEntityId();
  }

  private static void refreshIfRequired() {
    if (availableIds.size() < REQUIRED_ID_POOL_SIZE) {
      if (Bukkit.isPrimaryThread()) {
        refillEntityIds();
      } else {
        Synchronizer.synchronize(IdentifierReserve::refillEntityIds);
      }
    }
  }

  private static void refillEntityIds() {
    int missing = (REQUIRED_ID_POOL_SIZE - availableIds.size());
    if (missing > 0) {
      Arrays.stream(reserveEntityIds(missing)).forEach(availableIds::add);
    }
  }

  private static int[] reserveEntityIds(int amount) {
    return IntStream.range(0, amount).map(i -> reserveEntityId()).toArray();
  }

  private static int reserveEntityId() {
    int newId = 0;
    try {
      newId = ENTITY_COUNT_FIELD.getInt(null);
      ENTITY_COUNT_FIELD.setInt(null, newId + 1);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return newId;
  }
}
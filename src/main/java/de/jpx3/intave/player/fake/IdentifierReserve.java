package de.jpx3.intave.player.fake;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.klass.Lookup;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Entity Ids can only be aquired sync, but we need our ids async
 * So we reserve us a bunch of ids so we can use them later
 */
public final class IdentifierReserve {
  private final static Field ENTITY_COUNT_FIELD = Lookup.serverField("Entity", "entityCount");
  private final static int REQUIRED_ID_POOL_SIZE = 25;

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

  private final static boolean ATOMIC_INTEGER_FIELD = MinecraftVersions.VER1_14_0.atOrAbove();

  private static int reserveEntityId() {
    int newId = 0;
    try {
      if (ATOMIC_INTEGER_FIELD) {
        AtomicInteger atomicInteger = (AtomicInteger) ENTITY_COUNT_FIELD.get(null);
        newId = atomicInteger.getAndIncrement();
      } else {
        newId = ENTITY_COUNT_FIELD.getInt(null);
        ENTITY_COUNT_FIELD.setInt(null, newId + 1);
      }
    } catch (IllegalAccessException exception) {
      exception.printStackTrace();
    }
    return newId;
  }
}
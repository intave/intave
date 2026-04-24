package de.jpx3.intave.player.fake;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Entity Ids can only be aquired sync, but we need our ids async
 * So we reserve us a bunch of ids so we can use them later
 */
public final class IdentifierReserve {
  private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(1_500_000_000);
  private static final int REQUIRED_ID_POOL_SIZE = 25;

  private static final Queue<Integer> availableIds = new ConcurrentLinkedDeque<>();

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
      refillEntityIds();
    }
  }

  private static void refillEntityIds() {
    int missing = (REQUIRED_ID_POOL_SIZE - availableIds.size());
    if (missing > 0) {
      IntStream.of(reserveEntityIds(missing)).forEach(availableIds::add);
    }
  }

  private static int[] reserveEntityIds(int amount) {
    return IntStream.range(0, amount).map(i -> reserveEntityId()).toArray();
  }

  private static int reserveEntityId() {
    return NEXT_ENTITY_ID.getAndIncrement();
  }
}

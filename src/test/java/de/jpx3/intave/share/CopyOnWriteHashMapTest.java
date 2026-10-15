/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.share;

import de.jpx3.intave.share.opt.CopyOnWriteHashMap;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyOnWriteHashMapTest {

  @Test
  void resolvesEqualButNonIdenticalKeys() {
    CopyOnWriteHashMap<UUID, Object> map = new CopyOnWriteHashMap<>();
    UUID storedKey = UUID.randomUUID();
    UUID equalKey = new UUID(storedKey.getMostSignificantBits(), storedKey.getLeastSignificantBits());
    Object value = new Object();

    map.put(storedKey, value);

    assertSame(value, map.get(equalKey));
    assertTrue(map.containsKey(equalKey));
    assertSame(value, map.remove(equalKey));
    assertTrue(map.isEmpty());
  }

  @Test
  void replacesTheValueForAnEqualKey() {
    CopyOnWriteHashMap<String, Object> map = new CopyOnWriteHashMap<>();
    String storedKey = new String("key");
    String equalKey = new String("key");
    Object first = new Object();
    Object second = new Object();

    assertNull(map.put(storedKey, first));
    assertSame(first, map.put(equalKey, second));
    assertSame(second, map.get(storedKey));
    assertEquals(1, map.size());
  }

  @Test
  void resolvesDenseHashCollisions() {
    CopyOnWriteHashMap<CollisionKey, Integer> map = new CopyOnWriteHashMap<>();
    int keyCount = 256;
    for (int index = 0; index < keyCount; index++) {
      map.put(new CollisionKey(index), index);
    }

    for (int index = 0; index < keyCount; index++) {
      assertEquals(index, map.get(new CollisionKey(index)));
    }
    for (int index = 0; index < keyCount; index += 2) {
      assertEquals(index, map.remove(new CollisionKey(index)));
    }
    for (int index = 1; index < keyCount; index += 2) {
      assertEquals(index, map.get(new CollisionKey(index)));
    }
  }

  @Test
  void traversalUsesAStableSnapshotWhileEntriesAreRemoved() {
    CopyOnWriteHashMap<Integer, Integer> map = new CopyOnWriteHashMap<>();
    int entryCount = 1_000;
    for (int index = 0; index < entryCount; index++) {
      map.put(index, index);
    }
    Set<Integer> visited = new HashSet<>();

    map.forEach((key, value) -> {
      assertEquals(key, value);
      visited.add(key);
      assertEquals(value, map.remove(key));
    });

    assertEquals(entryCount, visited.size());
    assertTrue(map.isEmpty());
  }

  @Test
  void equalKeyReadsRemainStableDuringMutation() throws Exception {
    CopyOnWriteHashMap<UUID, Integer> map = new CopyOnWriteHashMap<>();
    int stableKeyCount = 64;
    UUID[] storedKeys = new UUID[stableKeyCount];
    UUID[] lookupKeys = new UUID[stableKeyCount];
    for (int index = 0; index < stableKeyCount; index++) {
      storedKeys[index] = UUID.randomUUID();
      lookupKeys[index] = new UUID(
        storedKeys[index].getMostSignificantBits(), storedKeys[index].getLeastSignificantBits()
      );
      map.put(storedKeys[index], index);
    }

    int readerCount = 8;
    int readsPerReader = 500_000;
    ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
    CountDownLatch start = new CountDownLatch(1);
    Future<?>[] readers = new Future<?>[readerCount];
    try {
      for (int reader = 0; reader < readerCount; reader++) {
        readers[reader] = executor.submit(() -> {
          assertTrue(start.await(5, TimeUnit.SECONDS));
          for (int read = 0; read < readsPerReader; read++) {
            int index = read & (stableKeyCount - 1);
            if (!Integer.valueOf(index).equals(map.get(lookupKeys[index]))) {
              throw new AssertionError("Observed a missing or incorrect stable mapping");
            }
          }
          return null;
        });
      }
      Future<?> writer = executor.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        for (int write = 0; write < 2_000; write++) {
          UUID transientKey = UUID.randomUUID();
          map.put(transientKey, write);
          assertEquals(write, map.remove(new UUID(
            transientKey.getMostSignificantBits(), transientKey.getLeastSignificantBits()
          )));
        }
        return null;
      });
      start.countDown();
      writer.get(20, TimeUnit.SECONDS);
      for (Future<?> reader : readers) {
        reader.get(20, TimeUnit.SECONDS);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }

  private static final class CollisionKey {
    private final int value;

    private CollisionKey(int value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof CollisionKey && value == ((CollisionKey) other).value;
    }

    @Override
    public int hashCode() {
      return 1;
    }
  }
}

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

import de.jpx3.intave.share.opt.CopyOnWriteIdentityMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopyOnWriteIdentityMapTest {

  @Test
  void distinguishesEqualButNonIdenticalKeys() {
    CopyOnWriteIdentityMap<String, Object> map = new CopyOnWriteIdentityMap<>();
    String first = new String("same");
    String second = new String("same");
    Object firstValue = new Object();
    Object secondValue = new Object();

    map.put(first, firstValue);
    map.put(second, secondValue);

    assertSame(firstValue, map.get(first));
    assertSame(secondValue, map.get(second));
    assertEquals(2, map.size());
  }

  @Test
  void supportsInsertReplaceRemoveAndClear() {
    CopyOnWriteIdentityMap<Object, Object> map = new CopyOnWriteIdentityMap<>();
    Object key = new Object();
    Object first = new Object();
    Object second = new Object();

    assertTrue(map.isEmpty());
    assertNull(map.put(key, first));
    assertSame(first, map.put(key, second));
    assertSame(second, map.get(key));
    assertSame(second, map.putIfAbsent(key, first));
    assertSame(second, map.remove(key));
    assertNull(map.get(key));
    assertFalse(map.containsKey(key));

    map.put(key, first);
    map.clear();
    assertTrue(map.isEmpty());
    assertEquals(0, map.size());

    assertNull(map.putIfAbsent(key, first));
    assertSame(first, map.computeIfAbsent(key, ignored -> {
      throw new AssertionError("Existing value must not be recomputed");
    }));
  }

  @Test
  void resizesAndRetainsEveryIdentityMapping() {
    CopyOnWriteIdentityMap<Object, Integer> map = new CopyOnWriteIdentityMap<>();
    int keyCount = 4_096;
    Object[] keys = new Object[keyCount];
    for (int index = 0; index < keyCount; index++) {
      keys[index] = new Object();
      assertNull(map.put(keys[index], index));
    }

    assertEquals(keyCount, map.size());
    for (int index = 0; index < keyCount; index++) {
      assertEquals(index, map.get(keys[index]));
    }

    for (int index = 0; index < keyCount; index += 2) {
      assertEquals(index, map.remove(keys[index]));
    }
    assertEquals(keyCount / 2, map.size());
    for (int index = 0; index < keyCount; index++) {
      if ((index & 1) == 0) {
        assertNull(map.get(keys[index]));
      } else {
        assertEquals(index, map.get(keys[index]));
      }
    }
  }

  @Test
  void rebuildsProbeClustersAfterRemoval() {
    CopyOnWriteIdentityMap<Object, Integer> map = new CopyOnWriteIdentityMap<>();
    int capacity = 32;
    int collisionCount = capacity / 2;
    Object[] collidingKeys = new Object[collisionCount];
    int found = 0;
    while (found < collisionCount) {
      Object candidate = new Object();
      if (tableIndex(candidate, capacity - 1) == 0) {
        collidingKeys[found++] = candidate;
      }
    }
    for (int index = 0; index < collidingKeys.length; index++) {
      map.put(collidingKeys[index], index);
    }

    assertEquals(7, map.remove(collidingKeys[7]));
    for (int index = 0; index < collidingKeys.length; index++) {
      if (index == 7) {
        assertNull(map.get(collidingKeys[index]));
      } else {
        assertEquals(index, map.get(collidingKeys[index]));
      }
    }
  }

  @Test
  void computesAColdKeyOnlyOnceUnderContention() throws Exception {
    CopyOnWriteIdentityMap<Object, Object> map = new CopyOnWriteIdentityMap<>();
    Object key = new Object();
    Object value = new Object();
    AtomicInteger computations = new AtomicInteger();
    int threadCount = 16;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Object>> results = new ArrayList<>(threadCount);
    try {
      for (int thread = 0; thread < threadCount; thread++) {
        results.add(executor.submit(() -> {
          assertTrue(start.await(5, TimeUnit.SECONDS));
          return map.computeIfAbsent(key, ignored -> {
            computations.incrementAndGet();
            return value;
          });
        }));
      }
      start.countDown();
      for (Future<Object> result : results) {
        assertSame(value, result.get(10, TimeUnit.SECONDS));
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
    }

    assertEquals(1, computations.get());
  }

  @Test
  void readersRemainStableWhileOtherKeysAreMutated() throws Exception {
    CopyOnWriteIdentityMap<Object, Integer> map = new CopyOnWriteIdentityMap<>();
    int stableKeyCount = 64;
    Object[] stableKeys = new Object[stableKeyCount];
    for (int index = 0; index < stableKeyCount; index++) {
      stableKeys[index] = new Object();
      map.put(stableKeys[index], index);
    }

    int readerCount = 8;
    int readsPerReader = 500_000;
    ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> readers = new ArrayList<>(readerCount);
    try {
      for (int reader = 0; reader < readerCount; reader++) {
        readers.add(executor.submit(() -> {
          assertTrue(start.await(5, TimeUnit.SECONDS));
          for (int read = 0; read < readsPerReader; read++) {
            int index = read & (stableKeyCount - 1);
            if (!Integer.valueOf(index).equals(map.get(stableKeys[index]))) {
              throw new AssertionError("Observed a missing or incorrect stable mapping");
            }
          }
          return null;
        }));
      }
      Future<?> writer = executor.submit(() -> {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        for (int write = 0; write < 2_000; write++) {
          Object transientKey = new Object();
          map.put(transientKey, write);
          assertEquals(write, map.remove(transientKey));
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

  @Test
  void rejectsNullKeysValuesAndComputedValues() {
    CopyOnWriteIdentityMap<Object, Object> map = new CopyOnWriteIdentityMap<>();
    Object key = new Object();

    assertThrows(NullPointerException.class, () -> map.get(null));
    assertThrows(NullPointerException.class, () -> map.put(null, new Object()));
    assertThrows(NullPointerException.class, () -> map.put(key, null));
    assertThrows(NullPointerException.class, () -> map.putIfAbsent(key, null));
    assertThrows(NullPointerException.class, () -> map.computeIfAbsent(null, ignored -> new Object()));
    assertThrows(NullPointerException.class, () -> map.computeIfAbsent(key, null));
    assertThrows(NullPointerException.class, () -> map.computeIfAbsent(key, ignored -> null));
    assertThrows(NullPointerException.class, () -> map.remove(null));
  }

  private static int tableIndex(Object key, int mask) {
    int hash = System.identityHashCode(key);
    hash ^= hash >>> 16;
    return hash & mask;
  }
}

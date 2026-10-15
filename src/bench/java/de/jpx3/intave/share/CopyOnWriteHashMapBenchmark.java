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

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CopyOnWriteHashMapBenchmark {
  private static final int USER_COUNT = 3_000;
  private static final int WARMUP_READS = 10_000_000;
  private static final int MEASURED_READS = 50_000_000;
  private static final int MUTATION_CYCLES = 1_000;
  private static volatile long sink;

  public static void main(String[] args) {
    int measuredReads = args.length == 0 ? MEASURED_READS : Integer.parseInt(args[0]);
    UUID[] storedKeys = new UUID[USER_COUNT];
    UUID[] equalLookupKeys = new UUID[USER_COUNT];
    CopyOnWriteHashMap<UUID, Integer> copyOnWrite = new CopyOnWriteHashMap<>();
    ConcurrentHashMap<UUID, Integer> concurrent = new ConcurrentHashMap<>();
    for (int index = 0; index < USER_COUNT; index++) {
      UUID key = new UUID(index * 31L + 17L, index * 67L + 23L);
      storedKeys[index] = key;
      equalLookupKeys[index] = new UUID(key.getMostSignificantBits(), key.getLeastSignificantBits());
      copyOnWrite.put(key, index);
      concurrent.put(key, index);
    }

    runCopyOnWriteReads(copyOnWrite, equalLookupKeys, WARMUP_READS);
    printReads(
      "copy-on-write, 3,000 equal UUID keys", measuredReads,
      runCopyOnWriteReads(copyOnWrite, equalLookupKeys, measuredReads)
    );
    runConcurrentReads(concurrent, equalLookupKeys, WARMUP_READS);
    printReads(
      "concurrent hash map, 3,000 equal UUID keys", measuredReads,
      runConcurrentReads(concurrent, equalLookupKeys, measuredReads)
    );

    runCopyOnWriteMutations(copyOnWrite, 50);
    printMutations(
      "copy-on-write join/quit cycle at 3,000 users", MUTATION_CYCLES,
      runCopyOnWriteMutations(copyOnWrite, MUTATION_CYCLES)
    );
    runConcurrentMutations(concurrent, 50);
    printMutations(
      "concurrent hash map join/quit cycle at 3,000 users", MUTATION_CYCLES,
      runConcurrentMutations(concurrent, MUTATION_CYCLES)
    );
  }

  private static long runCopyOnWriteReads(
    CopyOnWriteHashMap<UUID, Integer> map, UUID[] keys, int operations
  ) {
    int keyIndex = 0;
    long total = 0;
    long started = System.nanoTime();
    for (int operation = 0; operation < operations; operation++) {
      total += map.get(keys[keyIndex]);
      if (++keyIndex == keys.length) {
        keyIndex = 0;
      }
    }
    sink = total;
    return System.nanoTime() - started;
  }

  private static long runConcurrentReads(
    ConcurrentHashMap<UUID, Integer> map, UUID[] keys, int operations
  ) {
    int keyIndex = 0;
    long total = 0;
    long started = System.nanoTime();
    for (int operation = 0; operation < operations; operation++) {
      total += map.get(keys[keyIndex]);
      if (++keyIndex == keys.length) {
        keyIndex = 0;
      }
    }
    sink = total;
    return System.nanoTime() - started;
  }

  private static long runCopyOnWriteMutations(
    CopyOnWriteHashMap<UUID, Integer> map, int cycles
  ) {
    long started = System.nanoTime();
    for (int cycle = 0; cycle < cycles; cycle++) {
      UUID key = new UUID(-1L, cycle);
      map.put(key, cycle);
      map.remove(key);
    }
    return System.nanoTime() - started;
  }

  private static long runConcurrentMutations(
    ConcurrentHashMap<UUID, Integer> map, int cycles
  ) {
    long started = System.nanoTime();
    for (int cycle = 0; cycle < cycles; cycle++) {
      UUID key = new UUID(-1L, cycle);
      map.put(key, cycle);
      map.remove(key);
    }
    return System.nanoTime() - started;
  }

  private static void printReads(String scenario, long operations, long elapsedNanos) {
    double nanosPerOperation = elapsedNanos / (double) operations;
    double operationsPerSecond = operations * 1_000_000_000.0 / elapsedNanos;
    System.out.printf(
      Locale.ROOT, "%s: %.2f ns/read, %.0f reads/s%n",
      scenario, nanosPerOperation, operationsPerSecond
    );
  }

  private static void printMutations(String scenario, long cycles, long elapsedNanos) {
    double microsPerCycle = elapsedNanos / (double) cycles / 1_000.0;
    System.out.printf(Locale.ROOT, "%s: %.2f us/cycle%n", scenario, microsPerCycle);
  }
}

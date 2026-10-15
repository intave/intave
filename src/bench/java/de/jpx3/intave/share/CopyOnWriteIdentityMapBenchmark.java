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

import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class CopyOnWriteIdentityMapBenchmark {
  private static final int WARMUP_OPERATIONS = 10_000_000;
  private static final int MEASURED_OPERATIONS = 50_000_000;
  private static volatile long sink;

  public static void main(String[] args) throws Exception {
    int measuredOperations = args.length == 0
      ? MEASURED_OPERATIONS
      : Integer.parseInt(args[0]);
    CopyOnWriteIdentityMap<Object, Integer> map = new CopyOnWriteIdentityMap<>();
    Object singleKey = new Object();
    map.put(singleKey, 1);
    Object[] manyKeys = new Object[64];
    for (int index = 0; index < manyKeys.length; index++) {
      manyKeys[index] = new Object();
      map.put(manyKeys[index], index + 1);
    }

    runSingleKey(map, singleKey, WARMUP_OPERATIONS);
    print("single-key get", measuredOperations, runSingleKey(map, singleKey, measuredOperations));
    runManyKeys(map, manyKeys, WARMUP_OPERATIONS);
    print(
      "64-key round-robin get", measuredOperations,
      runManyKeys(map, manyKeys, measuredOperations)
    );
    runConcurrent(map, singleKey, measuredOperations);
  }

  private static long runSingleKey(
    CopyOnWriteIdentityMap<Object, Integer> map, Object key, int operations
  ) {
    long total = 0;
    long started = System.nanoTime();
    for (int index = 0; index < operations; index++) {
      total += map.get(key);
    }
    sink = total;
    return System.nanoTime() - started;
  }

  private static long runManyKeys(
    CopyOnWriteIdentityMap<Object, Integer> map, Object[] keys, int operations
  ) {
    long total = 0;
    long started = System.nanoTime();
    for (int index = 0; index < operations; index++) {
      total += map.get(keys[index & (keys.length - 1)]);
    }
    sink = total;
    return System.nanoTime() - started;
  }

  private static void runConcurrent(
    CopyOnWriteIdentityMap<Object, Integer> map, Object key, int operations
  ) throws InterruptedException {
    int threads = Math.min(16, Math.max(2, Runtime.getRuntime().availableProcessors()));
    int operationsPerThread = operations / threads;
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    Thread[] workers = new Thread[threads];
    long[] totals = new long[threads];
    for (int thread = 0; thread < threads; thread++) {
      int workerIndex = thread;
      workers[thread] = new Thread(() -> {
        ready.countDown();
        try {
          start.await();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          return;
        }
        long total = 0;
        for (int index = 0; index < operationsPerThread; index++) {
          total += map.get(key);
        }
        totals[workerIndex] = total;
      }, "copy-on-write-identity-map-benchmark-" + thread);
      workers[thread].start();
    }
    ready.await();
    long started = System.nanoTime();
    start.countDown();
    for (Thread worker : workers) {
      worker.join();
    }
    long elapsed = System.nanoTime() - started;
    long total = 0;
    for (long value : totals) {
      total += value;
    }
    sink = total;
    print("single-key concurrent x" + threads, (long) operationsPerThread * threads, elapsed);
  }

  private static void print(String scenario, long operations, long elapsedNanos) {
    double nanosPerOperation = elapsedNanos / (double) operations;
    double operationsPerSecond = operations * 1_000_000_000.0 / elapsedNanos;
    System.out.printf(
      Locale.ROOT, "%s: %.2f ns/op, %.0f ops/s%n",
      scenario, nanosPerOperation, operationsPerSecond
    );
  }
}

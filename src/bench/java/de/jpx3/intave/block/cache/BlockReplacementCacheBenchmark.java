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

package de.jpx3.intave.block.cache;

import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.Position;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import org.bukkit.Material;

import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Compares the production cache with experimental alternatives.
 * Each result uses a fresh JVM, two seconds of warmup and four seconds of measurement.
 * Throughput is closed-loop; latency samples are individual service times, not queueing
 * latency. Every 256-read batch is also timed so a scan blocking an unsampled read is
 * visible. Histogram percentiles use upper bounds with approximately 3% precision.
 * Writers remain active throughout measurement. This is not a server replay.
 * Matrix arguments: [forks] [comma-separated variants]. Variants: current, shared,
 * deduplicated, retained, striped16. With no arguments, compares current and shared.
 */
public final class BlockReplacementCacheBenchmark {
  private static volatile long sink;
  private static final long SECOND = 1_000_000_000L;

  private enum Scenario {
    PLAYERS(64, 256, 4, false, false),
    LARGE(16, 4096, 4, false, false),
    HOT(1, 256, 1, true, false),
    HOT_LARGE(1, 4096, 1, true, false),
    EXPIRY(1, 16384, 1, false, true);

    final int caches, entries, readers;
    final boolean saturated, expiry;
    Scenario(int caches, int entries, int readers, boolean saturated, boolean expiry) {
      this.caches = caches; this.entries = entries; this.readers = readers;
      this.saturated = saturated; this.expiry = expiry;
    }
  }

  private interface Cache {
    BlockCacheEntry get(long key);
    boolean contains(long key);
    void insert(Position position, BlockCacheEntry state);
    void remove(long key);
    void lock(Position position);
    boolean unlock(Position position);
    void refresh();
    void reset(int minX, int maxX, int minZ, int maxZ);
    boolean bounds(int minX, int maxX, int minZ, int maxZ);
    void clear();
    int size();
  }

  private static final class Current implements Cache {
    final BlockStateReplacementCache cache = new BlockStateReplacementCache(BlockReplacementCacheBenchmark::key);
    public BlockCacheEntry get(long key) { return cache.byKey(key); }
    public boolean contains(long key) { return cache.contains(key); }
    public void insert(Position p, BlockCacheEntry s) { cache.insert(p, s); }
    public void remove(long key) { cache.remove(key); }
    public void lock(Position p) { cache.lock(p); }
    public boolean unlock(Position p) { return cache.unlock(p); }
    public void refresh() { cache.internalRefresh(); }
    public void reset(int a, int b, int c, int d) { cache.chunkReset(a, b, c, d); }
    public boolean bounds(int a, int b, int c, int d) { return cache.hasOverridesInBounds(a, b, c, d); }
    public void clear() { cache.clear(); }
    public int size() { return cache.indexed().size(); }
  }

  // Benchmark-only candidate. The production cache is deliberately untouched.
  private static final class Shared implements Cache {
    final Map<Position, BlockCacheEntry> located;
    final Map<Position, Long> locked;
    final Long2ReferenceOpenHashMap<BlockCacheEntry> indexed;
    Shared() { this(96); }
    Shared(int expected) {
      located = new HashMap<>(expected);
      locked = new HashMap<>(expected);
      indexed = new Long2ReferenceOpenHashMap<>(expected);
    }
    public synchronized BlockCacheEntry get(long key) { return indexed.get(key); }
    public synchronized boolean contains(long key) { return indexed.containsKey(key); }
    public synchronized void insert(Position p, BlockCacheEntry state) {
      located.put(p, state);
      indexed.put(key(p), state);
    }
    public synchronized void remove(long key) { indexed.remove(key); }
    public synchronized void lock(Position p) { locked.put(p, System.currentTimeMillis()); }
    public synchronized boolean unlock(Position p) { return locked.remove(p) != null; }
    private boolean isLocked(Position p) {
      Long time = locked.get(p);
      return time != null && System.currentTimeMillis() - time < 5000L;
    }
    public synchronized void refresh() {
      Iterator<Map.Entry<Position, BlockCacheEntry>> iterator = located.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<Position, BlockCacheEntry> entry = iterator.next();
        Position p = entry.getKey();
        if (!isLocked(p) && entry.getValue().expired()) {
          iterator.remove();
          indexed.remove(key(p));
          locked.remove(p);
        }
      }
      locked.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 10000L);
    }
    public synchronized void reset(int minX, int maxX, int minZ, int maxZ) {
      Iterator<Position> iterator = located.keySet().iterator();
      while (iterator.hasNext()) {
        Position p = iterator.next();
        if (!isLocked(p) && inside(p, minX, maxX, minZ, maxZ)) {
          iterator.remove();
          indexed.remove(key(p));
          locked.remove(p);
        }
      }
    }
    public synchronized boolean bounds(int minX, int maxX, int minZ, int maxZ) {
      for (Position p : located.keySet()) if (inside(p, minX, maxX, minZ, maxZ)) return true;
      return false;
    }
    public synchronized void clear() { located.clear(); locked.clear(); indexed.clear(); }
    public synchronized int size() { return indexed.size(); }
  }

  // Same read-side synchronization as production, with duplicate bookkeeping removed.
  // This intentionally does not claim to fix the production cache's cross-map races.
  private static class Deduplicated implements Cache {
    final Map<Position, BlockCacheEntry> located = new ConcurrentHashMap<>(96);
    final Map<Position, Long> locked = new ConcurrentHashMap<>(96);
    final Long2ReferenceMap<BlockCacheEntry> indexed = Long2ReferenceMaps.synchronize(new Long2ReferenceOpenHashMap<>(96));
    public BlockCacheEntry get(long key) { return indexed.get(key); }
    public boolean contains(long key) { return indexed.containsKey(key); }
    public void insert(Position p, BlockCacheEntry s) { located.put(p, s); indexed.put(key(p), s); }
    public void remove(long key) { indexed.remove(key); }
    public void lock(Position p) { locked.put(p, System.currentTimeMillis()); }
    public boolean unlock(Position p) { return locked.remove(p) != null; }
    private boolean isLocked(Position p) {
      Long time = locked.get(p);
      return time != null && System.currentTimeMillis() - time < 5000L;
    }
    public void refresh() {
      for (Map.Entry<Position, BlockCacheEntry> entry : located.entrySet()) {
        Position p = entry.getKey();
        if (!isLocked(p) && entry.getValue().expired()) {
          located.remove(p);
          indexed.remove(key(p));
          locked.remove(p);
        }
      }
      locked.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 10000L);
    }
    public void reset(int a, int b, int c, int d) {
      for (Position p : located.keySet()) {
        if (!isLocked(p) && inside(p, a, b, c, d)) {
          located.remove(p);
          indexed.remove(key(p));
          locked.remove(p);
        }
      }
    }
    public boolean bounds(int a, int b, int c, int d) {
      for (Position p : located.keySet()) if (inside(p, a, b, c, d)) return true;
      return false;
    }
    public void clear() { locked.clear(); located.clear(); indexed.clear(); }
    public int size() { return indexed.size(); }
  }

  // Keep an invalidated slot until expiry/chunk cleanup. The next override replaces
  // its value without shifting the open-addressed table on every remove/insert pair.
  // get still returns null while invalidated; real cleanup still physically removes it.
  private static final class Retained extends Deduplicated {
    public void remove(long key) { indexed.replace(key, null); }
    public boolean contains(long key) { return indexed.get(key) != null; }
    public int size() {
      synchronized (indexed) {
        int visible = 0;
        for (BlockCacheEntry state : indexed.values()) if (state != null) visible++;
        return visible;
      }
    }
  }

  // Per-key mutations and expiry are atomic within one partition. Reads acquire only
  // that partition's monitor; refresh/reset release each partition before the next.
  private static final class Striped implements Cache {
    final Shared[] partitions = new Shared[16];
    Striped() { for (int i = 0; i < partitions.length; i++) partitions[i] = new Shared(8); }
    Shared partition(long key) { return partitions[(int) HashCommon.mix(key) & (partitions.length - 1)]; }
    public BlockCacheEntry get(long key) { return partition(key).get(key); }
    public boolean contains(long key) { return partition(key).contains(key); }
    public void insert(Position p, BlockCacheEntry s) { partition(key(p)).insert(p, s); }
    public void remove(long key) { partition(key).remove(key); }
    public void lock(Position p) { partition(key(p)).lock(p); }
    public boolean unlock(Position p) { return partition(key(p)).unlock(p); }
    public void refresh() { for (Shared partition : partitions) partition.refresh(); }
    public void reset(int a, int b, int c, int d) { for (Shared partition : partitions) partition.reset(a, b, c, d); }
    public boolean bounds(int a, int b, int c, int d) {
      for (Shared partition : partitions) if (partition.bounds(a, b, c, d)) return true;
      return false;
    }
    public void clear() { for (Shared partition : partitions) partition.clear(); }
    public int size() { int size = 0; for (Shared partition : partitions) size += partition.size(); return size; }
  }

  private static boolean inside(Position p, int a, int b, int c, int d) {
    return p.getX() >= a && p.getX() < b && p.getZ() >= c && p.getZ() < d;
  }
  private static long key(Position p) {
    return (p.getBlockX() & 0x3fffffL) << 42 | (p.getBlockY() & 0xfffffL) | (p.getBlockZ() & 0x3fffffL) << 20;
  }
  private static BlockCacheEntry state() {
    return new BlockCacheEntry(BlockShapes.emptyShape(), BlockShapes.emptyShape(), Material.STONE, 0);
  }
  private static BlockCacheEntry expiredState() throws Exception {
    BlockCacheEntry state = state();
    Field creation = BlockCacheEntry.class.getDeclaredField("creation");
    creation.setAccessible(true);
    creation.setLong(state, System.currentTimeMillis() - 20_000);
    if (!state.expired()) throw new AssertionError("Expiry fixture is not expired");
    return state;
  }
  private static Cache cache(String variant) {
    return switch (variant) {
      case "current" -> new Current();
      case "shared" -> new Shared();
      case "deduplicated" -> new Deduplicated();
      case "retained" -> new Retained();
      case "striped16" -> new Striped();
      default -> throw new IllegalArgumentException(variant);
    };
  }

  private static final class Stats {
    long operations, hits, missedBursts;
    final long[] latencies = new long[2048];
    long samples, maximum, batchMaximum;
    void sample(long latency) {
      int exponent = 63 - Long.numberOfLeadingZeros(latency);
      int bucket = latency < 32 ? (int) latency
        : 32 + (exponent - 5) * 32 + (int) ((latency >>> (exponent - 5)) & 31);
      latencies[bucket]++;
      samples++;
      maximum = Math.max(maximum, latency);
    }
    long percentile(double fraction) {
      if (fraction == 1) return maximum;
      if (samples == 0) return 0;
      long target = (long) Math.ceil(samples * fraction), count = 0;
      for (int i = 0; i < latencies.length; i++) {
        count += latencies[i];
        if (count >= target) return i < 32 ? i : ((33L + (i - 32) % 32) << ((i - 32) / 32)) - 1;
      }
      throw new AssertionError("Invalid histogram");
    }
    void add(Stats other) {
      operations += other.operations; hits += other.hits; missedBursts += other.missedBursts;
      samples += other.samples;
      maximum = Math.max(maximum, other.maximum);
      batchMaximum = Math.max(batchMaximum, other.batchMaximum);
      for (int i = 0; i < latencies.length; i++) latencies[i] += other.latencies[i];
    }
  }

  private static final class Run {
    volatile int phase;
    final CountDownLatch ready, start = new CountDownLatch(1);
    final AtomicReference<Throwable> failure = new AtomicReference<>();
    Run(int threads) { ready = new CountDownLatch(threads); }
    Thread start(String name, Runnable action) {
      Thread thread = new Thread(() -> {
        ready.countDown();
        try { start.await(); action.run(); }
        catch (Throwable error) { failure.compareAndSet(null, error); phase = 2; }
      }, name);
      thread.start();
      return thread;
    }
  }

  private static void run(String variant, Scenario scenario, int fork) throws Exception {
    Cache[] caches = new Cache[scenario.caches];
    Position[] positions = new Position[scenario.entries];
    long[] keys = new long[positions.length];
    BlockCacheEntry fresh = state(), expired = expiredState();
    for (int i = 0; i < positions.length; i++) {
      positions[i] = new Position(1000 + (i & 63), 64 + ((i >> 12) & 63), 1000 + ((i >> 6) & 63));
      keys[i] = key(positions[i]);
    }
    for (int i = 0; i < caches.length; i++) {
      Cache cache = caches[i] = cache(variant);
      for (int j = 0; j < positions.length; j++) {
        cache.insert(positions[j], scenario.expiry && (j & 3) == 0 ? expired : fresh);
        if ((j & 63) == 0) cache.lock(positions[j]);
      }
    }
    Run run = new Run(scenario.readers + 2);
    List<Thread> threads = new ArrayList<>();
    Stats[] readers = new Stats[scenario.readers];
    for (int r = 0; r < readers.length; r++) {
      Stats stats = readers[r] = new Stats();
      int readerId = r;
      threads.add(run.start("cache-reader-" + r, () -> {
        int random = 123456789 + readerId * 98761;
        long sequence = 0, checksum = 0;
        while (run.phase != 2) {
          boolean measured = run.phase == 1;
          long batchStart = measured ? System.nanoTime() : 0;
          for (int j = 0; j < 256; j++) {
            random ^= random << 13; random ^= random >>> 17; random ^= random << 5;
            int player = caches.length == 1 ? 0 : ((random >>> 16) & (caches.length / readers.length - 1)) * readers.length + readerId;
            // Include 25% misses without changing key generation between variants.
            long key = keys[random & (keys.length - 1)] ^ ((random & 12) == 0 ? 1L << 19 : 0);
            boolean sample = measured && ((sequence++ & 255) == 0);
            long start = sample ? System.nanoTime() : 0;
            BlockCacheEntry value = caches[player].get(key);
            if (sample) stats.sample(System.nanoTime() - start);
            if (value != null) { checksum += value.variantIndex() + 1; if (measured) stats.hits++; }
          }
          if (measured) {
            stats.batchMaximum = Math.max(stats.batchMaximum, System.nanoTime() - batchStart);
            stats.operations += 256;
          }
        }
        sink = checksum;
      }));
    }
    Stats writes = new Stats(), maintenance = new Stats();
    threads.add(run.start("cache-writer", () -> {
      long burst = 0, next = System.nanoTime();
      int index = 0;
      while (run.phase != 2) {
        boolean measured = run.phase == 1;
        // 128 updates every 50 ms, or continuous writes in saturation scenarios.
        for (int j = 0; j < 128; j++, index++) {
          Cache cache = caches[index & (caches.length - 1)];
          Position p = positions[(index / caches.length) & (positions.length - 1)];
          Position update = new Position(p.getX(), p.getY(), p.getZ());
          BlockCacheEntry state = scenario.expiry && (index & 3) == 0 ? expired : state();
          boolean sample = measured && (index & 15) == 0;
          long start = sample ? System.nanoTime() : 0;
          cache.remove(key(update));
          cache.insert(update, state);
          if ((index & 63) == 0) cache.lock(update);
          if (sample) writes.sample(System.nanoTime() - start);
          if (measured) writes.operations++;
        }
        burst++;
        if (!scenario.saturated) {
          next += SECOND / 20;
          long remaining = next - System.nanoTime();
          if (remaining > 0) LockSupport.parkNanos(remaining);
          else if (measured) writes.missedBursts++;
        }
      }
      sink = burst;
    }));
    threads.add(run.start("cache-maintenance", () -> {
      long next = System.nanoTime();
      int round = 0;
      while (run.phase != 2) {
        boolean measured = run.phase == 1;
        for (Cache cache : caches) {
          long start = measured ? System.nanoTime() : 0;
          cache.refresh();
          // A non-matching chunk query/reset forces a complete scan without draining the cache.
          if ((round & 1) == 0) cache.bounds(-16, 0, -16, 0);
          else cache.reset(-16, 0, -16, 0);
          if (measured) { maintenance.operations++; maintenance.sample(System.nanoTime() - start); }
        }
        round++;
        next += SECOND / 2;
        long remaining = next - System.nanoTime();
        if (remaining > 0) LockSupport.parkNanos(remaining);
      }
    }));
    run.ready.await();
    run.start.countDown();
    Thread.sleep(2000);
    if (run.failure.get() != null) throw new AssertionError(run.failure.get());
    long start = System.nanoTime();
    run.phase = 1;
    Thread.sleep(4000);
    run.phase = 2;
    long elapsed = System.nanoTime() - start;
    for (Thread thread : threads) { LockSupport.unpark(thread); thread.join(10000); if (thread.isAlive()) throw new AssertionError("Worker did not stop"); }
    if (run.failure.get() != null) throw new AssertionError(run.failure.get());
    Stats reads = new Stats();
    for (Stats stats : readers) reads.add(stats);
    double seconds = elapsed / (double) SECOND;
    System.out.printf(Locale.ROOT, "%s,%s,%d,%.3f,%.0f,%.0f,%d,%d,%d,%d,%d,%d,%d,%d,%.4f%n",
      variant, scenario, fork, seconds, reads.operations / seconds, writes.operations / seconds,
      reads.percentile(.99), reads.percentile(.999), reads.percentile(1),
      reads.batchMaximum, writes.percentile(.99), maintenance.percentile(.99), maintenance.percentile(1), writes.missedBursts,
      reads.hits / (double) reads.operations);
  }

  private static void verify(String variant) throws Exception {
    Stats histogram = new Stats(), other = new Stats();
    histogram.sample(100);
    other.sample(1000);
    histogram.add(other);
    if (histogram.percentile(.5) != 101 || histogram.percentile(.99) != 1007 || histogram.percentile(1) != 1000) {
      throw new AssertionError("Invalid latency histogram merge");
    }
    Cache current = new Current(), shared = cache(variant);
    BlockCacheEntry fresh = state(), expired = expiredState();
    Position[] ps = new Position[64];
    for (int i = 0; i < ps.length; i++) ps[i] = new Position(i - 32, 64, i & 7);
    Random random = new Random(12345);
    for (int i = 0; i < 2000; i++) {
      Position p = ps[random.nextInt(ps.length)];
      int operation = random.nextInt(9);
      for (Cache cache : new Cache[]{current, shared}) {
        switch (operation) {
          case 0 -> cache.insert(p, fresh);
          case 1 -> cache.insert(p, expired);
          case 2 -> cache.remove(key(p));
          case 3 -> cache.lock(p);
          case 4 -> cache.unlock(p);
          case 5 -> cache.refresh();
          case 6 -> cache.reset(0, 16, 0, 16);
          case 7 -> { if (i % 100 == 0) cache.clear(); }
          default -> cache.bounds(-32, 0, 0, 8);
        }
      }
      for (Position position : ps) {
        if (current.get(key(position)) != shared.get(key(position)) || current.contains(key(position)) != shared.contains(key(position))) {
          throw new AssertionError("Different state after operation " + operation);
        }
      }
      if (current.size() != shared.size() || current.bounds(-32, 0, 0, 8) != shared.bounds(-32, 0, 0, 8)) throw new AssertionError("Different cache contents");
    }
    if (shared instanceof Retained retained) {
      Position p = new Position(1, 64, 1);
      long key = key(p);
      retained.clear();
      retained.remove(key);
      if (!retained.indexed.isEmpty()) throw new AssertionError("Invalidating absent keys must not allocate slots");
      retained.insert(p, expired);
      retained.remove(key);
      if (!retained.indexed.containsKey(key) || retained.contains(key) || retained.size() != 0) {
        throw new AssertionError("Invalidated slots must remain invisible");
      }
      retained.refresh();
      if (!retained.indexed.isEmpty()) throw new AssertionError("Expiry must physically remove invalidated slots");
      retained.insert(p, fresh);
      retained.remove(key);
      retained.lock(p);
      retained.reset(0, 16, 0, 16);
      if (!retained.indexed.containsKey(key)) throw new AssertionError("Chunk cleanup must respect prediction locks");
      retained.unlock(p);
      retained.reset(0, 16, 0, 16);
      if (!retained.indexed.isEmpty()) throw new AssertionError("Chunk cleanup must physically remove invalidated slots");
    }
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 3) { run(args[0], Scenario.valueOf(args[1]), Integer.parseInt(args[2])); return; }
    String[] variants = args.length > 1 ? args[1].split(",") : new String[]{"current", "shared"};
    for (String variant : variants) verify(variant);
    System.out.println("# Sequential semantics verified; " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
    System.out.println("# " + System.getProperty("os.name") + " " + System.getProperty("os.arch")
      + "; logical CPUs=" + Runtime.getRuntime().availableProcessors() + "; per fork: 2s warmup, 4s measurement, 256 MiB heap");
    System.out.println("variant,scenario,fork,seconds,reads_per_s,writes_per_s,read_p99_ns,read_p999_ns,read_max_ns,read_batch_max_ns,write_p99_ns,maintenance_p99_ns,maintenance_max_ns,late_write_bursts,hit_ratio");
    int forks = args.length == 0 ? 3 : Integer.parseInt(args[0]);
    String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    for (int fork = 0; fork < forks; fork++) {
      for (Scenario scenario : Scenario.values()) {
        for (int v = 0; v < variants.length; v++) {
          String variant = variants[(v + fork) % variants.length];
          Process child = new ProcessBuilder(java, "-Xms256m", "-Xmx256m", "-cp", System.getProperty("java.class.path"),
            BlockReplacementCacheBenchmark.class.getName(), variant, scenario.name(), Integer.toString(fork)).inheritIO().start();
          if (child.waitFor() != 0) throw new AssertionError("Benchmark child failed");
        }
      }
    }
  }
}

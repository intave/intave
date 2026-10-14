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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.block.store.BlockStore;
import de.jpx3.intave.block.store.CopyOnWriteArrayLocalBlockStore;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class MultiChunkKeyBlockCacheTest {
  private static final ShapeResolverPipeline SHAPE_RESOLVER = new ShapeResolverPipeline() {
    @Override
    public BlockShape collisionShapeOf(
      World world, User user, Material type, int variant, int x, int y, int z
    ) {
      return BlockShapes.originCube().contextualized(x, y, z);
    }

    @Override
    public BlockShape outlineShapeOf(
      World world, User user, Material type, int variant, int x, int y, int z
    ) {
      return collisionShapeOf(world, user, type, variant, x, y, z);
    }
  };

  @BeforeEach
  void setupVersion() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
  }

  @Test
  void cachedStateIsReturnedWithoutAccessingTheWorld() {
    Fixture fixture = fixture();
    fixture.initializeOrigin(1600, 160, -1600);
    fixture.put(1600, 160, -1600, BlockState.stone());

    assertSame(BlockState.stone(), fixture.cache.stateAt(1600, 160, -1600));
    assertSame(BlockState.stone(), fixture.cache.peekStateAt(1600, 160, -1600));
    assertEquals(0, fixture.store.clearCount);
  }

  @Test
  void emptyCacheAdoptsFirstRemotePositionWithoutClearing() {
    Fixture fixture = fixture();

    fixture.initializeOrigin(1600, 160, -1600);
    fixture.put(1600, 160, -1600, BlockState.stone());

    assertSame(BlockState.stone(), fixture.cache.stateAt(1600, 160, -1600));
    assertEquals(0, fixture.store.clearCount);
  }

  @Test
  void explicitlyClearedCacheAdoptsANewRemoteOrigin() {
    Fixture fixture = fixture();
    fixture.initializeOrigin(0, 64, 0);
    fixture.put(0, 64, 0, BlockState.stone());
    fixture.cache.invalidateCache();

    fixture.initializeOrigin(1600, 160, -1600);
    fixture.put(1600, 160, -1600, BlockState.stone());

    assertSame(BlockState.stone(), fixture.cache.stateAt(1600, 160, -1600));
    assertEquals(1, fixture.store.clearCount);
  }

  @Test
  void positionsAtResetThresholdRemainCachedOnEveryAxis() {
    Fixture fixture = fixture();
    int x = 1600, y = 160, z = -1600;
    fixture.initializeOrigin(x, y, z);
    fixture.put(x, y, z, BlockState.stone());
    fixture.put(x + 32, y + 32, z, BlockState.stone());
    fixture.put(x, y, z - 32, BlockState.stone());

    assertSame(BlockState.stone(), fixture.cache.stateAt(x + 32, y + 32, z));
    assertSame(BlockState.stone(), fixture.cache.stateAt(x, y, z - 32));
    assertSame(BlockState.stone(), fixture.cache.peekStateAt(x, y, z));
    assertEquals(0, fixture.store.clearCount);
  }

  @Test
  void movingBeyondHorizontalXThresholdClearsTheLocalStore() {
    assertMovingBeyondThresholdClears(48, 0, 0);
  }

  @Test
  void movingBeyondVerticalThresholdClearsTheLocalStore() {
    assertMovingBeyondThresholdClears(0, 48, 0);
  }

  @Test
  void movingBeyondHorizontalZThresholdClearsTheLocalStore() {
    assertMovingBeyondThresholdClears(0, 0, -48);
  }

  @Test
  void diagonalHorizontalMovementUsesTheExistingRadialThreshold() {
    assertMovingBeyondThresholdClears(32, 0, 32);
  }

  @Test
  void resetMovesTheOriginToTheNewRegion() {
    Fixture fixture = fixture();
    int x = 1600, y = 160, z = -1600;
    fixture.initializeOrigin(x, y, z);
    fixture.put(x, y, z, BlockState.stone());
    fixture.overrideWithAir(x + 48, y, z);

    assertEquals(Material.AIR, fixture.cache.stateAt(x + 48, y, z).type());
    assertEquals(1, fixture.store.clearCount);
    fixture.cache.invalidateOverride(x + 48, y, z);
    fixture.put(x + 48, y, z, BlockState.stone());
    fixture.put(x + 80, y, z, BlockState.stone());

    assertSame(BlockState.stone(), fixture.cache.stateAt(x + 80, y, z));
    assertEquals(1, fixture.store.clearCount);

    fixture.overrideWithAir(x + 96, y, z);
    assertEquals(Material.AIR, fixture.cache.stateAt(x + 96, y, z).type());
    assertEquals(2, fixture.store.clearCount);
  }

  @Test
  void oversizedCacheClearsOnTheNextRegionChange() {
    Fixture fixture = fixture();
    int x = 1600, y = 160, z = -1600;
    fixture.initializeOrigin(x, y, z);
    fixture.put(x, y, z, BlockState.stone());
    fixture.store.reportedSize = 4097;
    fixture.overrideWithAir(x + 16, y, z);

    assertEquals(Material.AIR, fixture.cache.stateAt(x + 16, y, z).type());
    assertEquals(1, fixture.store.clearCount);
    assertNull(fixture.cache.peekStateAt(x, y, z));
  }

  @Test
  void targetedAndFullInvalidationRemoveExpectedEntries() {
    Fixture fixture = fixture();
    fixture.initializeOrigin(0, 64, 0);
    fixture.put(0, 64, 0, BlockState.stone());
    fixture.put(1, 64, 0, BlockState.stone());

    fixture.cache.invalidateCacheAt(0, 64, 0);
    assertNull(fixture.cache.peekStateAt(0, 64, 0));
    assertSame(BlockState.stone(), fixture.cache.peekStateAt(1, 64, 0));

    fixture.cache.invalidateCache();
    assertNull(fixture.cache.peekStateAt(1, 64, 0));
  }

  @RepeatedTest(10)
  void concurrentReadsAcrossTheRetainedWindowDoNotClearOrLoseEntries() throws Exception {
    Fixture fixture = fixture();
    int originX = 1600, originY = 160, originZ = -1600;
    fixture.initializeOrigin(originX, originY, originZ);
    int[][] offsets = {
      {0, 0, 0},
      {32, 0, 0}, {-32, 0, 0},
      {0, 32, 0}, {0, -32, 0},
      {0, 0, 32}, {0, 0, -32}
    };
    for (int[] offset : offsets) {
      fixture.put(originX + offset[0], originY + offset[1], originZ + offset[2], BlockState.stone());
    }

    int readerCount = 8;
    int readsPerReader = 10_000;
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(readerCount);
    Future<?>[] reads = new Future<?>[readerCount];
    try {
      for (int reader = 0; reader < readerCount; reader++) {
        final int readerIndex = reader;
        reads[reader] = executor.submit(() -> {
          assertTrue(start.await(5, TimeUnit.SECONDS));
          for (int index = 0; index < readsPerReader; index++) {
            int[] offset = offsets[(readerIndex + index) % offsets.length];
            BlockState state = fixture.cache.stateAt(
              originX + offset[0], originY + offset[1], originZ + offset[2]
            );
            assertSame(BlockState.stone(), state);
          }
          return null;
        });
      }

      start.countDown();
      for (Future<?> read : reads) {
        read.get(15, TimeUnit.SECONDS);
      }
    } finally {
      start.countDown();
      executor.shutdownNow();
    }

    assertEquals(0, fixture.store.clearCount);
    for (int[] offset : offsets) {
      assertSame(
        BlockState.stone(),
        fixture.cache.peekStateAt(
          originX + offset[0], originY + offset[1], originZ + offset[2]
        )
      );
    }
  }

  private static void assertMovingBeyondThresholdClears(int deltaX, int deltaY, int deltaZ) {
    Fixture fixture = fixture();
    int x = 1600, y = 160, z = -1600;
    fixture.initializeOrigin(x, y, z);
    fixture.put(x, y, z, BlockState.stone());
    fixture.overrideWithAir(x + deltaX, y + deltaY, z + deltaZ);

    assertEquals(
      Material.AIR,
      fixture.cache.stateAt(x + deltaX, y + deltaY, z + deltaZ).type()
    );
    assertEquals(1, fixture.store.clearCount);
    assertNull(fixture.cache.peekStateAt(x, y, z));
  }

  private static Fixture fixture() {
    TrackingBlockStore store = new TrackingBlockStore();
    MultiChunkKeyBlockCache cache = new MultiChunkKeyBlockCache(
      FakePlayerFactory.createPlayer(), SHAPE_RESOLVER, store
    );
    return new Fixture(cache, store);
  }

  private static final class Fixture {
    private final MultiChunkKeyBlockCache cache;
    private final TrackingBlockStore store;

    private Fixture(MultiChunkKeyBlockCache cache, TrackingBlockStore store) {
      this.cache = cache;
      this.store = store;
    }

    private void initializeOrigin(int x, int y, int z) {
      overrideWithAir(x, y, z);
      assertEquals(Material.AIR, cache.stateAt(x, y, z).type());
      cache.invalidateOverride(x, y, z);
    }

    private void overrideWithAir(int x, int y, int z) {
      cache.override(null, x, y, z, Material.AIR, 0, "TEST");
    }

    private void put(int x, int y, int z, BlockState state) {
      assertTrue(store.put(x, y, z, state));
    }
  }

  private static final class TrackingBlockStore implements BlockStore {
    private final BlockStore delegate = CopyOnWriteArrayLocalBlockStore.of();
    private int clearCount;
    private int reportedSize = -1;

    @Override
    public BlockState get(int x, int y, int z) {
      return delegate.get(x, y, z);
    }

    @Override
    public boolean put(int x, int y, int z, BlockState state) {
      return delegate.put(x, y, z, state);
    }

    @Override
    public int size() {
      return reportedSize < 0 ? delegate.size() : reportedSize;
    }

    @Override
    public void removeIf(Predicate<BlockState> predicate) {
      delegate.removeIf(predicate);
    }

    @Override
    public void clear() {
      clearCount++;
      reportedSize = -1;
      delegate.clear();
    }
  }
}

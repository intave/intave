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

package de.jpx3.intave.block.store;

import de.jpx3.intave.share.BlockCacheEntry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class CopyOnWriteArrayLocalBlockStoreTest {

	@Test
	public void testBasicInsertion() {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(0, 0, 0, BlockCacheEntry.stone()));

		assertEquals(BlockCacheEntry.stone(), store.get(0, 0, 0));
		assertNull(store.get(0, 1, 0));
		assertEquals(1, store.size());

		for (int i = 0; i < 1024; i++) {
			if (i <= 63) {
				assertTrue(store.put(i, 0, 0, BlockCacheEntry.stone()));
			} else {
				assertFalse(store.put(i, 0, 0, BlockCacheEntry.stone()));
			}
		}
	}

	@Test
	public void testReadOutsideUpperVerticalBoundary() {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(0, 0, 0, BlockCacheEntry.stone()));

		assertNull(store.get(8, 64, 0));
	}

	@Test
	public void testVerticalBoundaries() {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(0, 0, 0, BlockCacheEntry.stone()));
		assertTrue(store.put(0, -64, 0, BlockCacheEntry.stone()));
		assertTrue(store.put(0, 63, 0, BlockCacheEntry.stone()));

		assertEquals(BlockCacheEntry.stone(), store.get(0, -64, 0));
		assertEquals(BlockCacheEntry.stone(), store.get(0, 63, 0));
		assertFalse(store.put(0, -65, 0, BlockCacheEntry.stone()));
		assertFalse(store.put(0, 64, 0, BlockCacheEntry.stone()));
		assertNull(store.get(0, -65, 0));
		assertNull(store.get(0, 64, 0));
		assertEquals(3, store.size());
	}

	@Test
	public void testVerticalWindowCanBeCenteredAtExtendedWorldHeights() {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(0, -3000, 0, BlockCacheEntry.stone()));
		assertEquals(BlockCacheEntry.stone(), store.get(0, -3000, 0));
		assertTrue(store.put(0, -2999, 0, BlockCacheEntry.empty()));
		assertEquals(BlockCacheEntry.empty(), store.get(0, -2999, 0));

		store.clear();

		assertTrue(store.put(0, 3000, 0, BlockCacheEntry.stone()));
		assertEquals(BlockCacheEntry.stone(), store.get(0, 3000, 0));
	}

	@Test
	public void testSectorArrayBoundaries() {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(0, 0, 0, BlockCacheEntry.stone()));
		assertTrue(store.put(-64, -64, -64, BlockCacheEntry.stone()));
		assertTrue(store.put(63, 63, 63, BlockCacheEntry.stone()));

		assertEquals(BlockCacheEntry.stone(), store.get(-64, -64, -64));
		assertEquals(BlockCacheEntry.stone(), store.get(63, 63, 63));
		assertFalse(store.put(-65, 0, 0, BlockCacheEntry.stone()));
		assertFalse(store.put(64, 0, 0, BlockCacheEntry.stone()));
		assertFalse(store.put(0, 0, -65, BlockCacheEntry.stone()));
		assertFalse(store.put(0, 0, 64, BlockCacheEntry.stone()));
		assertNull(store.get(-65, 0, 0));
		assertNull(store.get(64, 0, 0));
		assertNull(store.get(0, 0, -65));
		assertNull(store.get(0, 0, 64));
	}

	@Test
	public void testConcurrentClearAndRemoveIfPreserveSize() throws Exception {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(0, 0, 0, BlockCacheEntry.stone()));

		CountDownLatch predicateEntered = new CountDownLatch(1);
		CountDownLatch continueRemoval = new CountDownLatch(1);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Future<?> removal = executor.submit(() -> store.removeIf(state -> {
			predicateEntered.countDown();
			try {
				if (!continueRemoval.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("Timed out waiting to resume removal");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
			return true;
		}));

		try {
			assertTrue(predicateEntered.await(5, TimeUnit.SECONDS), "Removal did not reach the predicate");
			store.clear();
			continueRemoval.countDown();
			removal.get(5, TimeUnit.SECONDS);

			assertEquals(0, store.size());
			assertNull(store.get(0, 0, 0));
		} finally {
			continueRemoval.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	public void testConcurrentDistinctWritesPreserveAllEntries() throws Exception {
		CopyOnWriteArrayLocalBlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		assertTrue(store.put(8, 0, 0, BlockCacheEntry.stone()));

		int writerCount = 8;
		int writesPerWriter = 64;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(writerCount);
		Future<?>[] writes = new Future<?>[writerCount];
		try {
			for (int writer = 0; writer < writerCount; writer++) {
				int x = writer;
				writes[writer] = executor.submit(() -> {
					try {
						if (!start.await(5, TimeUnit.SECONDS)) {
							throw new AssertionError("Timed out waiting to start writes");
						}
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new AssertionError(exception);
					}
					for (int y = 0; y < writesPerWriter; y++) {
						assertTrue(store.put(x, y, 0, BlockCacheEntry.stone()));
					}
				});
			}

			start.countDown();
			for (Future<?> write : writes) {
				write.get(10, TimeUnit.SECONDS);
			}

			assertEquals(1 + writerCount * writesPerWriter, store.size());
			for (int x = 0; x < writerCount; x++) {
				for (int y = 0; y < writesPerWriter; y++) {
					assertEquals(BlockCacheEntry.stone(), store.get(x, y, 0));
				}
			}
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

}

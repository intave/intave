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
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

public class BlockStoreBenchmark {

	static void main() {
		BlockStore volatileArrayBlockStore = createVolatileArrayBlockStore();
		BlockStore mapBlockStore = createMapBlockStore();

		System.out.println("Benchmarking VolatileArrayBlockStore");
		benchmark(volatileArrayBlockStore);

		System.out.println("Benchmarking MapBlockStore");
		benchmark(mapBlockStore);

		System.out.println("Finished");
	}

	private static void benchmark(BlockStore store) {
		int sumA = 0;
		for (int i = 0; i < 100000; i++) {
			int randomX = (int) (Math.random() * 32) - 16;
			int randomY = (int) (Math.random() * 32) - 16;
			int randomZ = (int) (Math.random() * 32) - 16;
			BlockCacheEntry blockCacheEntry = store.get(randomX, randomY, randomZ);
			if (blockCacheEntry != null) {
				sumA += blockCacheEntry.variantIndex();
			}
		}

		long start = System.nanoTime();
		int sum = 0;
		for (int i = 0; i < 1_000_000; i++) {
			int centerX = (int) (Math.random() * 32) - 16;
			int centerY = (int) (Math.random() * 32) - 16;
			int centerZ = (int) (Math.random() * 32) - 16;

			for (int x = centerX - 3; x < centerX + 3; x++) {
				for (int y = centerY - 3; y < centerY + 3; y++) {
					for (int z = centerZ - 3; z < centerZ + 3; z++) {
						BlockCacheEntry blockCacheEntry = store.get(x, y, z);
						if (blockCacheEntry != null) {
							sum += blockCacheEntry.variantIndex();
						}
					}
				}
			}
			if (i % 1_000 == 0) {
				int randomX = (int) (Math.random() * 32) - 16;
				int randomY = (int) (Math.random() * 32) - 16;
				int randomZ = (int) (Math.random() * 32) - 16;
				store.put(randomX, randomY, randomZ, BlockCacheEntry.stone());
			}
		}
		long end = System.nanoTime();
		System.out.println("Took " + (end - start) / 1e6 + "ms");
		System.out.println("Sum: " + sum);
		System.out.println("SumA: " + sumA);
	}

	private static BlockStore createVolatileArrayBlockStore() {
		BlockStore store = CopyOnWriteArrayLocalBlockStore.of();
		fillStore(store);
		return store;
	}

	private static BlockStore createMapBlockStore() {
		BlockStore store = MapBlockStore.of(Long2ReferenceMaps.synchronize(new Long2ReferenceOpenHashMap<>(96)));
		fillStore(store);
		return store;
	}

	private static void fillStore(BlockStore store) {
		for (int x = -16; x < 16; x++) {
			for (int y = -16; y < 16; y++) {
				for (int z = -16; z < 16; z++) {
					int hash = Integer.hashCode(x) ^ Integer.hashCode(y) ^ Integer.hashCode(z);
					if (hash % 2 == 0) {
						store.put(x, y, z, BlockCacheEntry.empty());
					} else {
						store.put(x, y, z, BlockCacheEntry.stone());
					}
				}
			}
		}
	}
}

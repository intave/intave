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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public final class CopyOnWriteArrayLocalBlockStore implements BlockStore {
	private static final int X_SECTORS = 16;
	private static final int Y_SECTORS = 16;
	private static final int Z_SECTORS = 16;
	private static final int BLOCKS_PER_SECTOR = 512;
	private static final Snapshot EMPTY = new Snapshot(null, 0, 0, 0, 0);

	private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(EMPTY);

	private CopyOnWriteArrayLocalBlockStore() {
	}

	@Override
	public BlockCacheEntry get(int x, int y, int z) {
		Snapshot current = snapshot.get();
		BlockCacheEntry[] sector = sectorOf(current, x, y, z);
		if (sector == null) {
			return null;
		}
		return sector[blockIndexOf(x, y, z)];
	}

	@Override
	public boolean put(int x, int y, int z, BlockCacheEntry state) {
		while (true) {
			Snapshot current = snapshot.get();
			int sectorCenterX = current.size == 0 ? x >> 3 : current.sectorCenterX;
			int sectorCenterY = current.size == 0 ? y >> 3 : current.sectorCenterY;
			int sectorCenterZ = current.size == 0 ? z >> 3 : current.sectorCenterZ;
			int offsetSectorX = (x >> 3) - sectorCenterX + (X_SECTORS / 2);
			int offsetSectorY = (y >> 3) - sectorCenterY + (Y_SECTORS / 2);
			int offsetSectorZ = (z >> 3) - sectorCenterZ + (Z_SECTORS / 2);
			if (outsideSectorBounds(offsetSectorX, offsetSectorY, offsetSectorZ)) {
				return false;
			}

			BlockCacheEntry[] currentSector = sectorAt(
				current.sectors, offsetSectorX, offsetSectorY, offsetSectorZ
			);
			int blockIndex = blockIndexOf(x, y, z);
			BlockCacheEntry previous = currentSector == null ? null : currentSector[blockIndex];
			if (previous == state) {
				return true;
			}

			int updatedSize = current.size;
			if (previous == null) {
				updatedSize++;
			} else if (state == null) {
				updatedSize--;
			}
			Snapshot updated = updatedSize == 0
				? EMPTY
				: new Snapshot(
					copySectorsWith(current, offsetSectorX, offsetSectorY, offsetSectorZ, blockIndex, state),
					sectorCenterX, sectorCenterY, sectorCenterZ, updatedSize
				);
			if (snapshot.compareAndSet(current, updated)) {
				return true;
			}
		}
	}

	@Override
	public int size() {
		return snapshot.get().size;
	}

	@Override
	public void removeIf(Predicate<BlockCacheEntry> predicate) {
		while (true) {
			Snapshot current = snapshot.get();
			if (current.size == 0) {
				return;
			}
			Snapshot updated = removeMatching(current, predicate);
			if (updated == current || snapshot.compareAndSet(current, updated)) {
				return;
			}
		}
	}

	@Override
	public void clear() {
		snapshot.set(EMPTY);
	}

	private static BlockCacheEntry[] sectorOf(Snapshot snapshot, int x, int y, int z) {
		if (snapshot.sectors == null) {
			return null;
		}
		int offsetSectorX = (x >> 3) - snapshot.sectorCenterX + (X_SECTORS / 2);
		int offsetSectorY = (y >> 3) - snapshot.sectorCenterY + (Y_SECTORS / 2);
		int offsetSectorZ = (z >> 3) - snapshot.sectorCenterZ + (Z_SECTORS / 2);
		if (outsideSectorBounds(offsetSectorX, offsetSectorY, offsetSectorZ)) {
			return null;
		}
		return sectorAt(snapshot.sectors, offsetSectorX, offsetSectorY, offsetSectorZ);
	}

	private static BlockCacheEntry[] sectorAt(
		BlockCacheEntry[][][][] sectors, int sectorX, int sectorY, int sectorZ
	) {
		if (sectors == null) {
			return null;
		}
		BlockCacheEntry[][][] yLayer = sectors[sectorY];
		if (yLayer == null) {
			return null;
		}
		BlockCacheEntry[][] zRow = yLayer[sectorZ];
		if (zRow == null) {
			return null;
		}
		return zRow[sectorX];
	}

	private static BlockCacheEntry[][][][] copySectorsWith(
		Snapshot current,
		int sectorX, int sectorY, int sectorZ,
		int blockIndex, BlockCacheEntry state
	) {
		BlockCacheEntry[][][][] updatedSectors = current.sectors == null
			? new BlockCacheEntry[Y_SECTORS][][][]
			: current.sectors.clone();
		BlockCacheEntry[][][] currentYLayer = current.sectors == null ? null : current.sectors[sectorY];
		BlockCacheEntry[][][] updatedYLayer = currentYLayer == null
			? new BlockCacheEntry[Z_SECTORS][][]
			: currentYLayer.clone();
		BlockCacheEntry[][] currentZRow = currentYLayer == null ? null : currentYLayer[sectorZ];
		BlockCacheEntry[][] updatedZRow = currentZRow == null
			? new BlockCacheEntry[X_SECTORS][]
			: currentZRow.clone();
		BlockCacheEntry[] currentSector = currentZRow == null ? null : currentZRow[sectorX];
		BlockCacheEntry[] updatedSector = currentSector == null
			? new BlockCacheEntry[BLOCKS_PER_SECTOR]
			: currentSector.clone();

		updatedSector[blockIndex] = state;
		updatedZRow[sectorX] = updatedSector;
		updatedYLayer[sectorZ] = updatedZRow;
		updatedSectors[sectorY] = updatedYLayer;
		return updatedSectors;
	}

	private static Snapshot removeMatching(Snapshot current, Predicate<BlockCacheEntry> predicate) {
		BlockCacheEntry[][][][] updatedSectors = null;
		int updatedSize = current.size;
		for (int y = 0; y < current.sectors.length; y++) {
			BlockCacheEntry[][][] currentYLayer = current.sectors[y];
			if (currentYLayer == null) {
				continue;
			}
			BlockCacheEntry[][][] updatedYLayer = null;
			for (int z = 0; z < currentYLayer.length; z++) {
				BlockCacheEntry[][] currentZRow = currentYLayer[z];
				if (currentZRow == null) {
					continue;
				}
				BlockCacheEntry[][] updatedZRow = null;
				for (int x = 0; x < currentZRow.length; x++) {
					BlockCacheEntry[] currentSector = currentZRow[x];
					if (currentSector == null) {
						continue;
					}
					BlockCacheEntry[] updatedSector = null;
					for (int block = 0; block < currentSector.length; block++) {
						BlockCacheEntry state = currentSector[block];
						if (state == null || !predicate.test(state)) {
							continue;
						}
						if (updatedSectors == null) {
							updatedSectors = current.sectors.clone();
						}
						if (updatedYLayer == null) {
							updatedYLayer = currentYLayer.clone();
							updatedSectors[y] = updatedYLayer;
						}
						if (updatedZRow == null) {
							updatedZRow = currentZRow.clone();
							updatedYLayer[z] = updatedZRow;
						}
						if (updatedSector == null) {
							updatedSector = currentSector.clone();
							updatedZRow[x] = updatedSector;
						}
						updatedSector[block] = null;
						updatedSize--;
					}
				}
			}
		}
		if (updatedSectors == null) {
			return current;
		}
		return updatedSize == 0
			? EMPTY
			: new Snapshot(
				updatedSectors,
				current.sectorCenterX, current.sectorCenterY, current.sectorCenterZ,
				updatedSize
			);
	}

	private static int blockIndexOf(int x, int y, int z) {
		return (y & 7) * 64 + (z & 7) * 8 + (x & 7);
	}

	private static boolean outsideSectorBounds(int sectorX, int sectorY, int sectorZ) {
		return sectorX < 0 || sectorX >= X_SECTORS
			|| sectorY < 0 || sectorY >= Y_SECTORS
			|| sectorZ < 0 || sectorZ >= Z_SECTORS;
	}

	private static final class Snapshot {
		private final BlockCacheEntry[][][][] sectors;
		private final int sectorCenterX;
		private final int sectorCenterY;
		private final int sectorCenterZ;
		private final int size;

		private Snapshot(
			BlockCacheEntry[][][][] sectors,
			int sectorCenterX, int sectorCenterY, int sectorCenterZ,
			int size
		) {
			this.sectors = sectors;
			this.sectorCenterX = sectorCenterX;
			this.sectorCenterY = sectorCenterY;
			this.sectorCenterZ = sectorCenterZ;
			this.size = size;
		}
	}

	public static CopyOnWriteArrayLocalBlockStore of() {
		return new CopyOnWriteArrayLocalBlockStore();
	}
}

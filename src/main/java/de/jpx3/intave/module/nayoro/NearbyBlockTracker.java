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

package de.jpx3.intave.module.nayoro;

import ac.intave.samples.share.Block;
import ac.intave.samples.share.BlockUpdate;
import de.jpx3.intave.block.cache.BlockCache;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BoundingBox;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.share.ClientMath.floor;

final class NearbyBlockTracker {
	static final double NEARBY_BLOCK_RADIUS = 2.0D;
	static final double LOOK_AHEAD_DECAY = 0.8D;
	static final double MAX_LOOK_AHEAD = 2.0D;
	static final double MIN_LOOK_AHEAD = 0.01D;
	private static final int MAX_RECORDED_BLOCKS = 10_000;

	// Insertion order gives constant-time eviction without scanning the recorded world.
	private final Long2ObjectLinkedOpenHashMap<RecordedBlock> recordedBlocks =
		new Long2ObjectLinkedOpenHashMap<>();
	private double lookAheadDistance;

	public List<BlockUpdate> dirtyNearbyBlocks(
		BlockCache blockCache,
		BoundingBox playerBoundingBox,
		Vector lookDirection,
		double movementDistance
	) {
		// Accumulate recent travel and let the extra reach fade when movement stops.
		double travelled = Double.isFinite(movementDistance) ? Math.max(0.0D, movementDistance) : 0.0D;
		lookAheadDistance = Math.min(MAX_LOOK_AHEAD, lookAheadDistance * LOOK_AHEAD_DECAY + travelled);
		if (lookAheadDistance < MIN_LOOK_AHEAD) {
			lookAheadDistance = 0.0D;
		}
		double extendX = 0.0D;
		double extendY = 0.0D;
		double extendZ = 0.0D;
		// MovementMetadata provides a cached unit look vector; never mutate it here.
		if (lookAheadDistance > 0.0D
			&& Double.isFinite(lookDirection.getX())
			&& Double.isFinite(lookDirection.getY())
			&& Double.isFinite(lookDirection.getZ())
		) {
			extendX = lookDirection.getX() * lookAheadDistance;
			extendY = lookDirection.getY() * lookAheadDistance;
			extendZ = lookDirection.getZ() * lookAheadDistance;
		}
		int minX = floor(playerBoundingBox.minX - NEARBY_BLOCK_RADIUS + Math.min(0.0D, extendX));
		int minY = floor(playerBoundingBox.minY - NEARBY_BLOCK_RADIUS + Math.min(0.0D, extendY));
		int minZ = floor(playerBoundingBox.minZ - NEARBY_BLOCK_RADIUS + Math.min(0.0D, extendZ));
		int maxX = floor(playerBoundingBox.maxX + NEARBY_BLOCK_RADIUS + Math.max(0.0D, extendX));
		int maxY = floor(playerBoundingBox.maxY + NEARBY_BLOCK_RADIUS + Math.max(0.0D, extendY));
		int maxZ = floor(playerBoundingBox.maxZ + NEARBY_BLOCK_RADIUS + Math.max(0.0D, extendZ));
		List<BlockUpdate> updates = Collections.emptyList();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					updates = sampleBlock(blockCache, x, y, z, updates);
				}
			}
		}
		return updates;
	}

	private List<BlockUpdate> sampleBlock(
		BlockCache blockCache,
		int x,
		int y,
		int z,
		List<BlockUpdate> updates
	) {
		long positionKey = BlockPosition.toLong(x, y, z);
		BlockCacheEntry state = blockCache.stateAt(x, y, z);
		RecordedBlock previous = recordedBlocks.get(positionKey);
		if (state.type() == Material.AIR) {
			if (previous != null) {
				recordedBlocks.remove(positionKey);
				return addUpdate(updates, x, y, z, Block.AIR);
			}
			return updates;
		}
		if (previous != null && previous.state == state) {
			return updates;
		}

		Block current = SampleTypes.block(state, x, y, z);
		if (previous != null && current.equals(previous.block)) {
			// Cache refreshes may replace BlockState instances without changing their
			// serialized form. Remember the new identity for subsequent fast-path scans.
			previous.state = state;
			return updates;
		}
		if (previous == null && recordedBlocks.size() >= MAX_RECORDED_BLOCKS) {
			recordedBlocks.removeFirst();
		}
		recordedBlocks.put(positionKey, new RecordedBlock(state, current));
		return addUpdate(updates, x, y, z, current);
	}

	int recordedBlockCount() {
		return recordedBlocks.size();
	}

	private static List<BlockUpdate> addUpdate(List<BlockUpdate> updates, int x, int y, int z, Block block) {
		if (updates.isEmpty()) {
			updates = new ArrayList<>();
		}
		updates.add(new BlockUpdate(
			new ac.intave.samples.share.BlockPosition(x, y, z), block
		));
		return updates;
	}

	private static final class RecordedBlock {
		private BlockCacheEntry state;
		private final Block block;

		private RecordedBlock(BlockCacheEntry state, Block block) {
			this.state = state;
			this.block = block;
		}
	}
}

package de.jpx3.intave.block.cache;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.module.test.record.MaterialVariantStore;
import de.jpx3.intave.module.test.record.MovementRecording;
import de.jpx3.intave.share.BlockPosition;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class PlaybackBlockCacheView implements BlockCache {
	private final MovementRecording recording;
	private final Map<BlockPosition, MaterialVariantStore> blocks = new HashMap<>();

	public PlaybackBlockCacheView(MovementRecording recording) {
		this.recording = recording;
	}

	public void updateBlocks(
		Map<BlockPosition, MaterialVariantStore> dirtyBlocks
	) {
		blocks.putAll(dirtyBlocks);
	}

	@NotNull
	@Override
	public BlockState stateAt(int posX, int posY, int posZ) {
		MaterialVariantStore store = storeAt(posX, posY, posZ);
		BlockShape shape = collisionShapeAt(posX, posY, posZ);
		return new BlockState(shape, shape, store.type(), store.variantIndex());
	}

	@NotNull
	@Override
	public BlockShape outlineShapeAt(int posX, int posY, int posZ) {
		return collisionShapeAt(posX, posY, posZ);
	}

	@NotNull
	@Override
	public BlockShape collisionShapeAt(int posX, int posY, int posZ) {
		MaterialVariantStore store = storeAt(posX, posY, posZ);
		if (store.type() == Material.AIR) {
			return BlockShapes.emptyShape();
		}
		BlockShape shape = recording.collisionShapes()
			.getOrDefault(store.type(), Collections.emptyMap())
			.getOrDefault(store.variantIndex(), BlockShapes.emptyShape());
		return shape.contextualized(posX, posY, posZ);
	}

	@Override
	public boolean isClientSpeculatingAt(int posX, int posY, int posZ) {
		return false;
	}

	@Override
	public void setClientSpeculationValue(World world, int posX, int posY, int posZ, Material type, int variant, int sequenceNumber) {
	}

	@Override
	public void undoClientSpeculation(World world, int posX, int posY, int posZ) {
	}

	@Override
	public void moveClientSpeculationsToOverride(World world, int requiredSequenceNumber) {
	}

	@Override
	public boolean currentlyInOverride(int posX, int posY, int posZ) {
		return false;
	}

	@Override
	public void lockOverride(int posX, int posY, int posZ) {
	}

	@Override
	public void unlockOverride(int posX, int posY, int posZ) {
	}

	@Override
	public void invalidateOverride(int posX, int posY, int posZ) {
	}

	@Override
	public int numOfIndexedReplacements() {
		return 0;
	}

	@Override
	public int numOfLocatedReplacements() {
		return blocks.size();
	}

	@Override
	public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
	}

	@Override
	public boolean hasOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
		return false;
	}

	@Override
	public void invalidateAll() {
		blocks.clear();
	}

	@Override
	public void invalidateCache() {
	}

	@Override
	public void invalidateCacheAt(int posX, int posY, int posZ) {
	}

	private MaterialVariantStore storeAt(int posX, int posY, int posZ) {
		return blocks.getOrDefault(
			BlockPosition.of(posX, posY, posZ),
			MaterialVariantStore.air()
		);
	}
}

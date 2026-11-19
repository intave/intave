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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.codec.JsonStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import org.bukkit.Material;

import java.util.*;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

public final class BlockCacheEntry {
	private static final BlockCacheEntry EMPTY = new BlockCacheEntry(BlockShapes.emptyShape(), BlockShapes.emptyShape(), Material.AIR, 0);
	private static final BlockCacheEntry STONE = new BlockCacheEntry(BlockShapes.originCube(), BlockShapes.originCube(), Material.STONE, 0);
	public static final StreamCodec<JsonReader, JsonWriter, BlockCacheEntry> JSON_CODEC = object(
		field("outlineShape", BlockShape.JSON_CODEC, BlockCacheEntry::outlineShape, BlockShapes.emptyShape()),
		field("collisionShape", BlockShape.JSON_CODEC, BlockCacheEntry::collisionShape, BlockShapes.emptyShape()),
		field("type", JsonStreamCodecs.MATERIAL, BlockCacheEntry::type, Material.AIR),
		field("variantIndex", JsonStreamCodecs.INTEGER, BlockCacheEntry::variantIndex, 0),
		BlockCacheEntry::new
	).encodeOnly(
		field("properties", stringMapCodec(JSON_PRIMITIVE), BlockCacheEntry::properties, Collections.emptyMap())
	);

	private final BlockShape outlineShape;
	private final BlockShape collisionShape;
	private final Material type;
	private final int variantIndex;
	private volatile Map<String, Comparable<?>> properties;
	private final long creation = System.currentTimeMillis();
	private int hashCode = 0;

	public BlockCacheEntry(BlockShape outlineShape, BlockShape collisionShape, Material type, int variantIndex) {
		this.outlineShape = outlineShape;
		this.collisionShape = collisionShape;
		this.type = type;
		this.variantIndex = variantIndex;
	}

	/**
	 * Returns the bounding box of this block state.
	 *
	 * @return the bounding box of this block state
	 */
	public BlockShape outlineShape() {
		return outlineShape;
	}

	/**
	 * Retrieve the blocks bounding boxes
	 *
	 * @return the blocks bounding boxes
	 */
	public BlockShape collisionShape() {
		return collisionShape;
	}

	/**
	 * Retrieve the blocks type
	 *
	 * @return the blocks type
	 */
	public Material type() {
		return type;
	}

	/**
	 * Retrieve the blocks variant
	 *
	 * @return the blocks variant
	 */
	public int variantIndex() {
		return variantIndex;
	}

	/**
	 * Returns the named block-state properties represented by {@link #variantIndex()}.
	 * Property names are sorted to keep diagnostic output deterministic.
	 */
	public Map<String, Comparable<?>> properties() {
		Map<String, Comparable<?>> resolved = properties;
		if (resolved != null) {
			return resolved;
		}
		if (type == Material.AIR || !BlockVariantRegister.isIndexed(type)) {
			return Collections.emptyMap();
		}

		BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
		SortedMap<String, Comparable<?>> sorted = new TreeMap<>();
		for (String name : variant.propertyNames()) {
			Comparable<?> value = variant.propertyOf(name);
			if (value != null) {
				sorted.put(name, value);
			}
		}
		resolved = Collections.unmodifiableSortedMap(sorted);
		properties = resolved;
		return resolved;
	}

	/**
	 * Indicates if this entry effectively expired.
	 * Expiries neither have to be acknowledged nor followed - this only serves as a possible indicator
	 *
	 * @return whether the state is expired
	 */
	@Deprecated
	public boolean expired() {
		return !IntaveControl.IGNORE_CACHE_REFRESH_ON_SIMULATION_FAULT && age() > 10000;
	}

	long age() {
		return System.currentTimeMillis() - creation;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		BlockCacheEntry that = (BlockCacheEntry) o;
		if (variantIndex != that.variantIndex) return false;
		if (creation != that.creation) return false;
		if (!Objects.equals(collisionShape, that.collisionShape)) return false;
		return type == that.type;
	}

	@Override
	public int hashCode() {
		if (hashCode == 0) {
			int result = collisionShape != null ? collisionShape.hashCode() : 0;
			result = 31 * result + (type != null ? type.hashCode() : 0);
			result = 31 * result + variantIndex;
			result = 31 * result + Long.hashCode(creation);
			hashCode = result;
		}
		return hashCode;
	}

	public static BlockCacheEntry empty() {
		return EMPTY;
	}

	public static BlockCacheEntry stone() {
		return STONE;
	}
}

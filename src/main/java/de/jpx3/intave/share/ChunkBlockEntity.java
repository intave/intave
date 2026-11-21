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

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class ChunkBlockEntity {
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkBlockEntity> STREAM_CODEC = codec(ByteBufStreamCodecs.COMPOUND_TAG);
	public static final StreamCodec<ByteBuf, ByteBuf, ChunkBlockEntity> NAMED_NBT_STREAM_CODEC = codec(ByteBufStreamCodecs.NAMED_COMPOUND_TAG);

	private final int packedXZ, y, typeId;
	private final CompoundTag tag;

	public ChunkBlockEntity(int packedXZ, int y, int typeId, CompoundTag tag) {
		if (packedXZ < 0 || packedXZ > 255 || y < Short.MIN_VALUE || y > Short.MAX_VALUE || typeId < 0) {
			throw new IllegalArgumentException("Invalid chunk block entity coordinates or type ID");
		}
		this.packedXZ = packedXZ;
		this.y = y;
		this.typeId = typeId;
		this.tag = tag == null ? null : tag.copy();
	}

	public int packedXZ() {
		return packedXZ;
	}

	public int y() {
		return y;
	}

	public int typeId() {
		return typeId;
	}

	public CompoundTag tag() {
		return tag == null ? null : tag.copy();
	}

	private static StreamCodec<ByteBuf, ByteBuf, ChunkBlockEntity> codec(StreamCodec<ByteBuf, ByteBuf, CompoundTag> tags) {
		return StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, ChunkBlockEntity::packedXZ,
			ByteBufStreamCodecs.SIGNED_SHORT, ChunkBlockEntity::y,
			ByteBufStreamCodecs.VARINT, ChunkBlockEntity::typeId,
			tags, entity -> entity.tag,
			ChunkBlockEntity::new
		);
	}
}

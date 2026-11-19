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

import de.jpx3.intave.block.state.BlockStates;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.version.ServerProtocolVersion;
import io.netty.buffer.ByteBuf;
import org.bukkit.Material;

import java.util.*;

/**
 * A block position and its state ID in the packet's protocol version.
 */
public final class BlockUpdate {
	public static final StreamCodec<ByteBuf, ByteBuf, BlockUpdate> STREAM_CODEC = StreamCodec.compound(
		BlockPosition.WIRE_STREAM_CODEC, BlockUpdate::position,
		ByteBufStreamCodecs.VARINT, BlockUpdate::stateId,
		BlockUpdate::new
	);
	public static final StreamCodec<ByteBuf, ByteBuf, BlockUpdate> LEGACY_STREAM_CODEC = StreamCodec.compound(
		BlockPosition.LEGACY_STREAM_CODEC, BlockUpdate::position,
		ByteBufStreamCodecs.VARINT, BlockUpdate::stateId,
		BlockUpdate::new
	);

	/**
	 * Counted chunk updates used before 1.16.2, with an unsigned byte Y coordinate.
	 */
	public static StreamCodec<ByteBuf, ByteBuf, List<BlockUpdate>> chunkStreamCodec(int chunkX, int chunkZ) {
		return StreamCodec.of((output, updates) -> {
			ByteBufStreamCodecs.VARINT.encode(output, updates.size());
			for (BlockUpdate update : updates) {
				BlockPosition position = update.position();
				if ((position.getX() & ~15) != (chunkX << 4) || (position.getZ() & ~15) != (chunkZ << 4)
					|| position.getY() < 0 || position.getY() > 255)
					throw new IllegalArgumentException("Block update is outside its chunk");
				output.writeShort((position.getX() & 15) << 12 | (position.getZ() & 15) << 8 | position.getY());
				ByteBufStreamCodecs.VARINT.encode(output, update.stateId());
			}
		}, input -> {
			int count = ByteBufStreamCodecs.count(input, input.readableBytes() / 3);
			if (count > input.readableBytes() / 3) throw new IllegalArgumentException("Truncated block update records");
			List<BlockUpdate> updates = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				int packed = input.readUnsignedShort();
				BlockPosition position = new BlockPosition(
					(chunkX << 4) | (packed >> 12 & 15), packed & 255, (chunkZ << 4) | (packed >> 8 & 15)
				);
				updates.add(new BlockUpdate(position, ByteBufStreamCodecs.VARINT.decode(input)));
			}
			return Collections.unmodifiableList(updates);
		});
	}

	/**
	 * Counted section updates. Positions are absolute; the wire stores only their local coordinates.
	 */
	public static StreamCodec<ByteBuf, ByteBuf, List<BlockUpdate>> sectionStreamCodec(BlockPosition section) {
		Objects.requireNonNull(section, "section");
		return StreamCodec.of((output, updates) -> {
			ByteBufStreamCodecs.VARINT.encode(output, updates.size());
			for (BlockUpdate update : updates) {
				BlockPosition position = update.position();
				if (position.getX() >> 4 != section.getX() || position.getY() >> 4 != section.getY()
					|| position.getZ() >> 4 != section.getZ()) {
					throw new IllegalArgumentException("Block update is outside its section");
				}
				long packed = (long) update.stateId() << 12
					| (position.getX() & 15) << 8 | (position.getZ() & 15) << 4 | (position.getY() & 15);
				ByteBufStreamCodecs.VARLONG.encode(output, packed);
			}
		}, input -> {
			int count = ByteBufStreamCodecs.count(input, input.readableBytes());
			if (count > input.readableBytes()) throw new IllegalArgumentException("Truncated block update records");
			List<BlockUpdate> updates = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				long packed = ByteBufStreamCodecs.VARLONG.decode(input);
				long stateId = packed >>> 12;
				if (stateId > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid block state ID: " + stateId);
				updates.add(new BlockUpdate(new BlockPosition(
					(section.getX() << 4) | (int) (packed >> 8 & 15),
					(section.getY() << 4) | (int) (packed & 15),
					(section.getZ() << 4) | (int) (packed >> 4 & 15)
				), (int) stateId));
			}
			return Collections.unmodifiableList(updates);
		});
	}

	private final BlockPosition position;
	private final int stateId;

	public BlockUpdate(BlockPosition position, int stateId) {
		this.position = Objects.requireNonNull(position, "position");
		if (stateId < 0) {
			throw new IllegalArgumentException("Negative block state ID: " + stateId);
		}
		this.stateId = stateId;
	}

	public BlockPosition position() {
		return position;
	}

	public int stateId() {
		return stateId;
	}

	/**
	 * Resolve using the protocol of the encoded packet, after Intave's block variants have been indexed.
	 * The result belongs to the running server's material and variant register.
	 *
	 * @throws IllegalArgumentException      if the protocol or wire state ID is unknown
	 * @throws IllegalStateException         if the server's block variants have not been indexed
	 * @throws UnsupportedOperationException if the server cannot represent this block state
	 */
	public Optional<MaterialAndVariant> resolveFor(int protocolVersion) {
		return BlockStates.materialAndVariantOf(protocolVersion, stateId);
	}

	public Optional<MaterialAndVariant> resolve() {
		return resolveFor(ServerProtocolVersion.current());
	}

	public Optional<Material> material(int protocolVersion) {
		return resolveFor(protocolVersion).map(MaterialAndVariant::material);
	}

	public OptionalInt variantId(int protocolVersion) {
		Optional<MaterialAndVariant> resolve = resolveFor(protocolVersion);
		return resolve.map(materialAndVariant -> OptionalInt.of(materialAndVariant.variantId()))
			.orElseGet(OptionalInt::empty);
	}
}

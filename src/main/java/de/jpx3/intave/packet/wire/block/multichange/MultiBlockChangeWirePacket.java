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

package de.jpx3.intave.packet.wire.block.multichange;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableMultiBlockChangePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

import java.util.*;

public final class MultiBlockChangeWirePacket {
	private MultiBlockChangeWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol751.class,
			Protocol763.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableMultiBlockChangePacket {
		protected BlockPosition sectionPosition;
		protected List<BlockUpdate> updates;

		protected Base() {
			this(BlockPosition.ORIGIN, Collections.emptyList());
		}

		protected Base(BlockPosition sectionPosition, List<BlockUpdate> updates) {
			this.updates = checkedUpdates(sectionPosition, updates);
			this.sectionPosition = sectionPosition;
		}

		public BlockPosition sectionPosition() {
			return sectionPosition;
		}

		@Override
		public List<BlockUpdate> blockUpdates() {
			return updates;
		}

		/** Replaces the batch, deriving its section from the first update. Empty batches retain the header. */
		@Override
		public void setBlockUpdates(List<BlockUpdate> updates) {
			markMutated();
			BlockPosition section = updates.isEmpty() ? sectionPosition : sectionOf(updates.get(0).position());
			this.updates = checkedUpdates(section, updates);
			this.sectionPosition = section;
		}

		protected static BlockPosition sectionOf(BlockPosition position) {
			return new BlockPosition(position.getX() >> 4, position.getY() >> 4, position.getZ() >> 4);
		}

		protected static List<BlockUpdate> checkedUpdates(BlockPosition section, List<BlockUpdate> updates) {
			Objects.requireNonNull(section, "sectionPosition");
			if (section.getX() < -2097152 || section.getX() > 2097151
				|| section.getY() < -524288 || section.getY() > 524287
				|| section.getZ() < -2097152 || section.getZ() > 2097151) {
				throw new IllegalArgumentException("Section position is outside the packed wire range: " + section);
			}
			List<BlockUpdate> copy = new ArrayList<>(updates);
			for (BlockUpdate update : copy) {
				if (!section.equals(sectionOf(update.position()))) {
					throw new IllegalArgumentException("All block updates must belong to section " + section);
				}
			}
			return Collections.unmodifiableList(copy);
		}
	}

	static final class Protocol47 extends AbstractMutable implements MutableMultiBlockChangePacket {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					output.writeInt(packet.chunkX);
					output.writeInt(packet.chunkZ);
					BlockUpdate.chunkStreamCodec(packet.chunkX, packet.chunkZ).encode(output, packet.updates);
				},
				input -> {
					int chunkX = input.readInt();
					int chunkZ = input.readInt();
					return new Protocol47(chunkX, chunkZ, BlockUpdate.chunkStreamCodec(chunkX, chunkZ).decode(input));
				}
			);

		private int chunkX, chunkZ;
		private List<BlockUpdate> updates;

		public Protocol47() {
			this(0, 0, Collections.emptyList());
		}

		public Protocol47(int chunkX, int chunkZ, List<BlockUpdate> updates) {
			this.updates = checkedUpdates(chunkX, chunkZ, updates);
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 751;
		}

		public int chunkX() {
			return chunkX;
		}

		public int chunkZ() {
			return chunkZ;
		}

		@Override
		public List<BlockUpdate> blockUpdates() {
			return updates;
		}

		/** Replaces the batch, deriving its chunk from the first update. Empty batches retain the header. */
		@Override
		public void setBlockUpdates(List<BlockUpdate> updates) {
			markMutated();
			int x = updates.isEmpty() ? chunkX : updates.get(0).position().getX() >> 4;
			int z = updates.isEmpty() ? chunkZ : updates.get(0).position().getZ() >> 4;
			this.updates = checkedUpdates(x, z, updates);
			this.chunkX = x;
			this.chunkZ = z;
		}

		private static List<BlockUpdate> checkedUpdates(int chunkX, int chunkZ, List<BlockUpdate> updates) {
			List<BlockUpdate> copy = new ArrayList<>(updates);
			for (BlockUpdate update : copy) {
				BlockPosition position = update.position();
				if ((position.getX() & ~15) != (chunkX << 4) || (position.getZ() & ~15) != (chunkZ << 4)
					|| position.getY() < 0 || position.getY() > 255) {
					throw new IllegalArgumentException("Block updates must belong to the same chunk with Y in [0, 255]");
				}
			}
			return Collections.unmodifiableList(copy);
		}
	}

	static final class Protocol751 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol751> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					BlockPosition.SECTION_STREAM_CODEC.encode(output, packet.sectionPosition);
					output.writeBoolean(packet.suppressLightUpdates);
					BlockUpdate.sectionStreamCodec(packet.sectionPosition).encode(output, packet.updates);
				},
				input -> {
					BlockPosition section = BlockPosition.SECTION_STREAM_CODEC.decode(input);
					boolean suppressLightUpdates = input.readBoolean();
					return new Protocol751(section, suppressLightUpdates, BlockUpdate.sectionStreamCodec(section).decode(input));
				}
			);

		private boolean suppressLightUpdates;

		public Protocol751() {
			this(BlockPosition.ORIGIN, false, Collections.emptyList());
		}

		public Protocol751(
			BlockPosition sectionPosition, boolean suppressLightUpdates, List<BlockUpdate> updates
		) {
			super(sectionPosition, updates);
			this.suppressLightUpdates = suppressLightUpdates;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 751 && protocolVersion < 763;
		}

		public boolean suppressLightUpdates() {
			return suppressLightUpdates;
		}

		public void setSuppressLightUpdates(boolean suppressLightUpdates) {
			markMutated();
			this.suppressLightUpdates = suppressLightUpdates;
		}
	}

	static final class Protocol763 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					BlockPosition.SECTION_STREAM_CODEC.encode(output, packet.sectionPosition);
					BlockUpdate.sectionStreamCodec(packet.sectionPosition).encode(output, packet.updates);
				},
				input -> {
					BlockPosition section = BlockPosition.SECTION_STREAM_CODEC.decode(input);
					return new Protocol763(section, BlockUpdate.sectionStreamCodec(section).decode(input));
				}
			);

		public Protocol763() {
			super();
		}

		public Protocol763(BlockPosition sectionPosition, List<BlockUpdate> updates) {
			super(sectionPosition, updates);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 763;
		}
	}
}

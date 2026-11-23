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

package de.jpx3.intave.packet.wire.block.change;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableSingleBlockChangePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SingleBlockChangeWirePacket {
	private SingleBlockChangeWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol477.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableSingleBlockChangePacket {
		protected BlockUpdate update;

		protected Base() {
			this(BlockPosition.ORIGIN, 0);
		}

		protected Base(BlockPosition position, int stateId) {
			this(new BlockUpdate(position, stateId));
		}

		protected Base(BlockUpdate update) {
			this.update = checkedUpdate(update);
		}

		@Override
		public BlockPosition blockPosition() {
			return update.position();
		}

		public int stateId() {
			return update.stateId();
		}

		@Override
		public List<BlockUpdate> blockUpdates() {
			return Collections.singletonList(update);
		}

		@Override
		public void setBlockPosition(BlockPosition position) {
			markMutated();
			update = checkedUpdate(new BlockUpdate(position, update.stateId()));
		}

		public void setStateId(int stateId) {
			markMutated();
			update = new BlockUpdate(update.position(), stateId);
		}

		@Override
		public void setBlockUpdates(List<BlockUpdate> updates) {
			markMutated();
			if (updates.size() != 1) throw new IllegalArgumentException("Expected exactly one block update");
			update = checkedUpdate(updates.get(0));
		}

		protected static BlockUpdate checkedUpdate(BlockUpdate update) {
			BlockPosition position = Objects.requireNonNull(update, "update").position();
			if (position.getX() < -33554432 || position.getX() > 33554431
				|| position.getY() < -2048 || position.getY() > 2047
				|| position.getZ() < -33554432 || position.getZ() > 33554431) {
				throw new IllegalArgumentException("Block position is outside the packed wire range: " + position);
			}
			return update;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			BlockUpdate.LEGACY_STREAM_CODEC.beforeAndAfter(Protocol47::new, packet -> packet.update);

		public Protocol47() {
			super();
		}

		public Protocol47(BlockPosition position, int stateId) {
			super(position, stateId);
		}

		public Protocol47(BlockUpdate update) {
			super(update);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 477;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC =
			BlockUpdate.STREAM_CODEC.beforeAndAfter(Protocol477::new, packet -> packet.update);

		public Protocol477() {
			super();
		}

		public Protocol477(BlockPosition position, int stateId) {
			super(position, stateId);
		}

		public Protocol477(BlockUpdate update) {
			super(update);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 477;
		}
	}
}

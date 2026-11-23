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

package de.jpx3.intave.packet.wire.block;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableBlockDigPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerDigAction;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BlockDigWirePacket {
	private BlockDigWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol477.class,
			Protocol759.class,
			Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableBlockDigPacket {
		protected BlockPosition blockPosition;
		protected Direction direction;
		protected int actionId, sequence;

		protected Base() {
			this(0, BlockPosition.ORIGIN, Direction.DOWN, 0);
		}

		protected Base(
			int actionId, BlockPosition blockPosition,
			Direction direction, int sequence
		) {
			this.actionId = actionId;
			this.blockPosition = blockPosition;
			this.direction = direction;
			this.sequence = sequence;
		}

		@Override
		public BlockPosition blockPosition() {
			return this.blockPosition;
		}

		@Override
		public void setBlockPosition(BlockPosition value) {
			markMutated();
			this.blockPosition = value;
		}

		@Override
		public Direction direction() {
			return this.direction;
		}

		@Override
		public void setDirection(Direction value) {
			markMutated();
			this.direction = value;
		}

		@Override
		public abstract PlayerDigAction action();

		@Override
		public abstract void setAction(PlayerDigAction value);

		public int actionId() {
			return actionId;
		}

		public int sequence() {
			return sequence;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::actionId,
				BlockPosition.LEGACY_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.enumCodec(Direction.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), Base::direction,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(int actionId, BlockPosition blockPosition, Direction direction) {
			super(actionId, blockPosition, direction, 0);
		}

		@Override
		public PlayerDigAction action() {
			int ordinal = actionId;

			return ordinal >= 0 && ordinal < 8 ? PlayerDigAction.values()[ordinal] : null;
		}

		@Override
		public void setAction(PlayerDigAction value) {
			markMutated();
			if (value == PlayerDigAction.CHANGE_DESTROY_DIRECTION) {
				throw new IllegalArgumentException("Direction changes require 26.3");
			} else {
				actionId = value.ordinal();
			}
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 477;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::actionId,
				BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.enumCodec(Direction.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), Base::direction,
				Protocol477::new
			);

		public Protocol477() {
			super();
		}

		public Protocol477(
			int actionId, BlockPosition blockPosition, Direction direction) {
			super(actionId, blockPosition, direction, 0);
		}

		@Override
		public PlayerDigAction action() {
			int ordinal = actionId;

			return ordinal >= 0 && ordinal < 8 ? PlayerDigAction.values()[ordinal] : null;
		}

		@Override
		public void setAction(PlayerDigAction value) {
			markMutated();
			if (value == PlayerDigAction.CHANGE_DESTROY_DIRECTION) {
				throw new IllegalArgumentException("Direction changes require 26.3");
			} else {
				actionId = value.ordinal();
			}
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 477 && protocolVersion < 759;
		}
	}

	static final class Protocol759 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::actionId,
				BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.enumCodec(Direction.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), Base::direction,
				ByteBufStreamCodecs.VARINT, Base::sequence,
				Protocol759::new
			);

		public Protocol759() {
			super();
		}

		public Protocol759(
			int actionId, BlockPosition blockPosition, Direction direction, int sequence) {
			super(actionId, blockPosition, direction, sequence);
		}

		@Override
		public PlayerDigAction action() {
			int ordinal = actionId;

			return ordinal >= 0 && ordinal < 8 ? PlayerDigAction.values()[ordinal] : null;
		}

		@Override
		public void setAction(PlayerDigAction value) {
			markMutated();
			if (value == PlayerDigAction.CHANGE_DESTROY_DIRECTION) {
				throw new IllegalArgumentException("Direction changes require 26.3");
			} else {
				actionId = value.ordinal();
			}
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 759 && protocolVersion < 777;
		}
	}

	static final class Protocol777 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol777::actionId,
				BlockPosition.WIRE_STREAM_CODEC, Protocol777::blockPosition,
				ByteBufStreamCodecs.enumCodec(Direction.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), Protocol777::direction,
				ByteBufStreamCodecs.VARINT, Protocol777::sequence,
				Protocol777::new
			);

		public Protocol777() {
			super();
		}

		public Protocol777(
			int actionId, BlockPosition blockPosition,
			Direction direction, int sequence
		) {
			super(actionId, blockPosition, direction, sequence);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 777;
		}

		@Override
		public PlayerDigAction action() {
			int ordinal = (actionId > 1) ? actionId - 1 : actionId;
			if (actionId == 1) return PlayerDigAction.CHANGE_DESTROY_DIRECTION;
			return ordinal >= 0 && ordinal < 8 ? PlayerDigAction.values()[ordinal] : null;
		}

		@Override
		public void setAction(PlayerDigAction value) {
			markMutated();
			if (value == PlayerDigAction.CHANGE_DESTROY_DIRECTION) {
				actionId = 1;
			} else {
				actionId = value.ordinal() + ((value.ordinal() >= 1) ? 1 : 0);
			}
		}
	}
}

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
import de.jpx3.intave.item.ItemStack;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

public final class BlockInteractionWirePacket {
	private BlockInteractionWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol315.class,
			Protocol477.class,
			Protocol759.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableBlockInteractionPacket {
		protected BlockPosition blockPosition;
		protected int directionId;
		protected PlayerHand hand;
		protected RawVector3d hitPosition;

		protected Base() {
			this(BlockPosition.ORIGIN, 0, PlayerHand.MAIN_HAND, RawVector3d.zero());
		}

		protected Base(
			BlockPosition blockPosition,
			int directionId,
			PlayerHand hand,
			RawVector3d hitPosition) {
			this.blockPosition = blockPosition;
			this.directionId = directionId;
			this.hand = hand;
			this.hitPosition = hitPosition;
		}

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Client.USE_ITEM_ON;
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
		public PlayerHand hand() {
			return this.hand;
		}

		@Override
		public void setHand(PlayerHand value) {
			markMutated();
			this.hand = value;
		}

		@Override
		public RawVector3d hitPosition() {
			markMutated();
			return this.hitPosition;
		}

		@Override
		public void setHitPosition(RawVector3d value) {
			markMutated();
			this.hitPosition = value;
		}

		@Override
		public boolean isDirectItemUseRequest() {
			return false;
		}

		@Override
		public Direction direction() {
			return directionId >= 0 && directionId < 6 ? Direction.values()[directionId] : null;
		}

		@Override
		public void setDirection(Direction value) {
			markMutated();
			directionId = value == null ? 255 : value.ordinal();
		}

		@Override
		public OptionalInt sequenceNumber() {
			return OptionalInt.empty();
		}

		@Override
		public void setSequenceNumber(int value) {
			markMutated();
			throw new IllegalStateException("No sequence before 1.19");
		}

		public int directionId() {
			return directionId;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = StreamCodec.compound(
			BlockPosition.LEGACY_STREAM_CODEC, Base::blockPosition,
			ByteBufStreamCodecs.UNSIGNED_BYTE, Base::directionId,
			ItemStack.codecFor(47), Protocol47::item,
			RawVector3d.BYTE_HIT_POSITION_STREAM_CODEC, Base::hitPosition,
			Protocol47::new
		);

		private final ItemStack item;

		Protocol47() {
			this(BlockPosition.ORIGIN, 0, null, RawVector3d.zero());
		}

		Protocol47(BlockPosition position, int directionId, ItemStack item, RawVector3d hitPosition) {
			super(position, directionId, PlayerHand.MAIN_HAND, hitPosition);
			this.item = item == null ? null : item.copy();
		}

		ItemStack item() {
			return item == null ? null : item.copy();
		}

		@Override
		public boolean isItemUseRequest() {
			return isDirectItemUseRequest();
		}

		@Override
		public boolean isDirectItemUseRequest() {
			return directionId == 255;
		}

		@Override
		public void setHand(PlayerHand value) {
			if (value != PlayerHand.MAIN_HAND) {
				throw new IllegalArgumentException("Legacy block placement only uses the main hand");
			}
			markMutated();
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				BlockPosition.LEGACY_STREAM_CODEC, Protocol107::blockPosition,
				ByteBufStreamCodecs.VARINT, Protocol107::directionId,
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Protocol107::hand,
				RawVector3d.BYTE_HIT_POSITION_STREAM_CODEC, Protocol107::hitPosition,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(
			BlockPosition blockPosition,
			int directionId,
			PlayerHand hand,
			RawVector3d hitPosition) {
			super(blockPosition, directionId, hand, hitPosition);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 315;
		}
	}

	static class Protocol315 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol315> STREAM_CODEC =
			StreamCodec.compound(
				BlockPosition.LEGACY_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.VARINT, Base::directionId,
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Base::hand,
				RawVector3d.FLOAT_STREAM_CODEC, Base::hitPosition,
				Protocol315::new
			);

		public Protocol315() {
			this(BlockPosition.ORIGIN, 0, PlayerHand.MAIN_HAND, new RawVector3d(0, 0, 0));
		}

		public Protocol315(
			BlockPosition blockPosition,
			int directionId,
			PlayerHand hand,
			RawVector3d hitPosition) {
			super(blockPosition, directionId, hand, hitPosition);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 315 && protocolVersion < 477;
		}
	}

	static class Protocol477 extends BlockInteractionWirePacket.Protocol315 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Base::hand,
				BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.VARINT, Base::directionId,
				RawVector3d.FLOAT_STREAM_CODEC, Base::hitPosition,
				ByteBufStreamCodecs.BOOLEAN, Protocol477::inside,
				Protocol477::new
			);

		protected boolean inside;

		public Protocol477() {
			this(PlayerHand.MAIN_HAND, BlockPosition.ORIGIN, 0, new RawVector3d(0, 0, 0), false);
		}

		public Protocol477(
			PlayerHand hand,
			BlockPosition blockPosition,
			int directionId,
			RawVector3d hitPosition,
			boolean inside) {
			super(blockPosition, directionId, hand, hitPosition);
			this.inside = inside;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 477 && protocolVersion < 759;
		}

		public boolean inside() {
			return inside;
		}
	}

	static class Protocol759 extends BlockInteractionWirePacket.Protocol477 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Base::hand,
				BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.VARINT, Base::directionId,
				RawVector3d.FLOAT_STREAM_CODEC, Base::hitPosition,
				ByteBufStreamCodecs.BOOLEAN, BlockInteractionWirePacket.Protocol477::inside,
				ByteBufStreamCodecs.VARINT, Protocol759::sequence,
				Protocol759::new
			);

		protected int sequence;

		public Protocol759() {
			this(PlayerHand.MAIN_HAND, BlockPosition.ORIGIN, 0, new RawVector3d(0, 0, 0), false, 0);
		}

		public Protocol759(
			PlayerHand hand,
			BlockPosition blockPosition,
			int directionId,
			RawVector3d hitPosition,
			boolean inside,
			int sequence) {
			super(hand, blockPosition, directionId, hitPosition, inside);
			this.sequence = sequence;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 759 && protocolVersion < 768;
		}

		@Override
		public OptionalInt sequenceNumber() {
			return OptionalInt.of(sequence);
		}

		@Override
		public void setSequenceNumber(int value) {
			markMutated();
			sequence = value;
		}

		public int sequence() {
			return sequence;
		}
	}

	static class Protocol768 extends BlockInteractionWirePacket.Protocol759 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Base::hand,
				BlockPosition.WIRE_STREAM_CODEC, Base::blockPosition,
				ByteBufStreamCodecs.VARINT, Base::directionId,
				RawVector3d.FLOAT_STREAM_CODEC, Base::hitPosition,
				ByteBufStreamCodecs.BOOLEAN, BlockInteractionWirePacket.Protocol477::inside,
				ByteBufStreamCodecs.BOOLEAN, Protocol768::border,
				ByteBufStreamCodecs.VARINT, BlockInteractionWirePacket.Protocol759::sequence,
				Protocol768::new
			);

		protected boolean border;

		public Protocol768() {
			this(
				PlayerHand.MAIN_HAND,
				BlockPosition.ORIGIN,
				0,
				new RawVector3d(0, 0, 0),
				false, false,
				0
			);
		}

		public Protocol768(
			PlayerHand hand,
			BlockPosition blockPosition,
			int directionId,
			RawVector3d hitPosition,
			boolean inside, boolean border,
			int sequence
		) {
			super(hand, blockPosition, directionId, hitPosition, inside, sequence);
			this.border = border;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768;
		}

		public boolean border() {
			return border;
		}
	}
}

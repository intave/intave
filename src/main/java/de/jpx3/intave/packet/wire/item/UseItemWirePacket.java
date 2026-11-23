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

package de.jpx3.intave.packet.wire.item;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableBlockInteractionPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.*;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

public final class UseItemWirePacket {
	private UseItemWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol107.class,
			Protocol759.class,
			Protocol767.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableBlockInteractionPacket {
		protected PlayerHand hand;

		protected Base() {
			this(PlayerHand.MAIN_HAND);
		}

		protected Base(PlayerHand hand) {
			this.hand = hand;
		}

		@Override
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Client.USE_ITEM;
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
		public boolean isDirectItemUseRequest() {
			return true;
		}

		@Override
		public BlockPosition blockPosition() {
			return null;
		}

		@Override
		public void setBlockPosition(BlockPosition value) {
			markMutated();
			if (value != null) throw new IllegalArgumentException("Item use has no block position");
		}

		@Override
		public Direction direction() {
			return null;
		}

		@Override
		public void setDirection(Direction value) {
			markMutated();
			if (value != null) throw new IllegalArgumentException("Item use has no face");
		}

		@Override
		public RawVector3d hitPosition() {
			return null;
		}

		@Override
		public void setHitPosition(RawVector3d value) {
			markMutated();
			if (value != null) throw new IllegalArgumentException("Item use has no hit position");
		}

		@Override
		public abstract OptionalInt sequenceNumber();

		@Override
		public abstract void setSequenceNumber(int value);
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Protocol107::hand,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(PlayerHand hand) {
			super(hand);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 759;
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
	}

	static class Protocol759 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Base::hand,
				ByteBufStreamCodecs.VARINT, Protocol759::sequence,
				Protocol759::new
			);

		protected int sequence;

		public Protocol759() {
			this(PlayerHand.MAIN_HAND, 0);
		}

		public Protocol759(PlayerHand hand, int sequence) {
			super(hand);
			this.sequence = sequence;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 759 && protocolVersion < 767;
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

	static class Protocol767 extends UseItemWirePacket.Protocol759 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol767> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.enumCodec(PlayerHand.values()), Base::hand,
				ByteBufStreamCodecs.VARINT, UseItemWirePacket.Protocol759::sequence,
				Rotation.STREAM_CODEC, Protocol767::rotation,
				Protocol767::new
			);

		protected Rotation rotation;

		public Protocol767() {
			this(PlayerHand.MAIN_HAND, 0, new Rotation(0, 0));
		}

		public Protocol767(PlayerHand hand, int sequence, Rotation rotation) {
			super(hand, sequence);
			this.rotation = rotation;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 767;
		}

		public Rotation rotation() {
			markMutated();
			return rotation;
		}
	}
}

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

package de.jpx3.intave.packet.wire.player;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableArmAnimationPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.PlayerHand;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ArmAnimationWirePacket {
	private ArmAnimationWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableArmAnimationPacket {
		protected PlayerHand hand;

		protected Base() {
			this(PlayerHand.MAIN_HAND);
		}

		protected Base(PlayerHand hand) {
			this.hand = hand;
		}

		@Override
		public PlayerHand hand() {
			return this.hand;
		}

		@Override
		public abstract void setHand(PlayerHand value);
	}

	static final class Protocol47 extends Base {
		private final static Protocol47 INSTANCE = new Protocol47();

		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
				},
				input -> INSTANCE
			);

		public Protocol47() {
			super(null);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}

		@Override
		public void setHand(PlayerHand hand) {
			markMutated();
			throw new UnsupportedOperationException("This packet does not support a hand");
		}
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
			return protocolVersion >= 107;
		}

		@Override
		public void setHand(PlayerHand value) {
			markMutated();
			this.hand = value;
		}
	}
}

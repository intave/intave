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
import de.jpx3.intave.packet.contract.mutable.MutablePlayerInputPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.Input;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PlayerInputWirePacket {
	private PlayerInputWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol768.class
		));
	}

	static final class Protocol47 extends AbstractMutable implements MutablePlayerInputPacket {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.FLOAT, Protocol47::strafe,
				ByteBufStreamCodecs.FLOAT, Protocol47::forward,
				ByteBufStreamCodecs.UNSIGNED_BYTE, Protocol47::flags,
				Protocol47::new
			);

		private float forward, strafe;
		private int flags;

		public Protocol47() {
			this(0, 0, 0);
		}

		public Protocol47(float strafe, float forward, int flags) {
			this.strafe = strafe;
			this.forward = forward;
			this.flags = flags;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 768;
		}

		@Override
		public Input input() {
			return null;
		}

		@Override
		public Float forward() {
			return forward;
		}

		@Override
		public Float strafe() {
			return strafe;
		}

		@Override
		public boolean jumping() {
			return (flags & 1) != 0;
		}

		public boolean dismounting() {
			return (flags & 2) != 0;
		}

		@Override
		public void setAxes(float forward, float strafe) {
			markMutated();
			this.forward = forward;
			this.strafe = strafe;
		}

		@Override
		public void setJumping(boolean jumping) {
			markMutated();
			flags = jumping ? flags | 1 : flags & ~1;
		}

		public void setDismounting(boolean dismounting) {
			markMutated();
			flags = dismounting ? flags | 2 : flags & ~2;
		}

		@Override
		public void setInput(Input input) {
			markMutated();
			setAxes(input.forward(), input.strafe());
			setJumping(input.jumpKey());
			setDismounting(input.sneakKey());
		}

		public int flags() {
			return flags;
		}
	}

	static final class Protocol768 extends AbstractMutable implements MutablePlayerInputPacket {
		public final static StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC = Input.STREAM_CODEC.beforeAndAfter(
			Protocol768::new, Protocol768::input
		);

		private Input input;

		public Protocol768() {
			this.input = Input.none();
		}

		public Protocol768(Input input) {
			this.input = input;
		}

		@Override
		public Input input() {
			return input;
		}

		@Override
		public void setInput(Input input) {
			markMutated();
			this.input = input;
		}

		@Override
		public String toString() {
			return "PlayerInputWirePacket{" +
				"input=" + input +
				'}';
		}

		@Override
		public int hashCode() {
			int result = input != null ? input.hashCode() : 0;
			result = 31 * result + (input != null ? input.hashCode() : 0);
			return result;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			Protocol768 that = (Protocol768) o;
			return Objects.equals(input, that.input);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			// 1.21.2 replaced the analog axes and flags with a packed input byte.
			return protocolVersion >= 768;
		}
	}
}

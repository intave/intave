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

package de.jpx3.intave.packet.wire.window.button;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableMenuButtonPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MenuButtonWirePacket {
	private MenuButtonWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol766.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableMenuButtonPacket {
		protected int containerId;
		protected int button;

		protected Base() {
			this(0, 0);
		}

		protected Base(int containerId, int button) {
			this.containerId = containerId;
			this.button = button;
		}

		@Override
		public int containerId() {
			return this.containerId;
		}

		@Override
		public void setContainerId(int value) {
			markMutated();
			this.containerId = value;
		}

		@Override
		public int button() {
			return this.button;
		}

		@Override
		public void setButton(int value) {
			markMutated();
			this.button = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.SIGNED_BYTE, Base::containerId,
				ByteBufStreamCodecs.SIGNED_BYTE, Base::button,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(int containerId, int button) {
			super(containerId, button);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 766;
		}
	}

	static final class Protocol766 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol766::containerId,
				ByteBufStreamCodecs.VARINT, Protocol766::button,
				Protocol766::new
			);

		public Protocol766() {
			super();
		}

		public Protocol766(int containerId, int button) {
			super(containerId, button);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 766;
		}
	}
}

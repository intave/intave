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

package de.jpx3.intave.packet.wire.window;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableWindowClosePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class WindowCloseWirePacket {
	private WindowCloseWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableWindowClosePacket {
		protected int containerId;

		protected Base() {
			this(0);
		}

		protected Base(int containerId) {
			this.containerId = containerId;
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
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::containerId,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(int containerId) {
			super(containerId);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 768;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol768::containerId,
				Protocol768::new
			);

		public Protocol768() {
			super();
		}

		public Protocol768(int containerId) {
			super(containerId);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768;
		}
	}
}

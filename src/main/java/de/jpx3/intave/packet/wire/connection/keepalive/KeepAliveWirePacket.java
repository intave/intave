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

package de.jpx3.intave.packet.wire.connection.keepalive;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableKeepAlivePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KeepAliveWirePacket {
	private KeepAliveWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol340.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableKeepAlivePacket {
		protected long id;

		protected Base() {
			this(0);
		}

		protected Base(long id) {
			this.id = id;
		}

		@Override
		public long id() {
			return this.id;
		}

		@Override
		public void setId(long value) {
			markMutated();
			this.id = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT.beforeAndAfter(Integer::longValue, Math::toIntExact), Base::id,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(long id) {
			super(id);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 340;
		}
	}

	static final class Protocol340 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol340> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.LONG, Protocol340::id,
				Protocol340::new
			);

		public Protocol340() {
			super();
		}

		public Protocol340(long id) {
			super(id);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 340;
		}
	}
}

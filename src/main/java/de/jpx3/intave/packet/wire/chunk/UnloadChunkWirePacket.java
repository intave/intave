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

package de.jpx3.intave.packet.wire.chunk;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableUnloadChunkPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class UnloadChunkWirePacket {
	private UnloadChunkWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol107.class,
			Protocol764.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableUnloadChunkPacket {
		protected int x, z;

		protected Base() {
			this(0, 0);
		}

		protected Base(int z, int x) {
			this.z = z;
			this.x = x;
		}

		@Override
		public int chunkX(int index) {
			checkIndex(index);
			return x;
		}

		@Override
		public int chunkZ(int index) {
			checkIndex(index);
			return z;
		}

		@Override
		public void setChunkCoordinates(int index, int x, int z) {
			markMutated();
			checkIndex(index);
			this.x = x;
			this.z = z;
		}

		protected static void checkIndex(int index) {
			if (index != 0) throw new IndexOutOfBoundsException("Chunk index: " + index);
		}

		public int x() {
			return x;
		}

		public int z() {
			return z;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.INTEGER, Base::x,
				ByteBufStreamCodecs.INTEGER, Base::z,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(int x, int z) {
			super(z, x);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 764;
		}
	}

	static final class Protocol764 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.INTEGER, Protocol764::z,
				ByteBufStreamCodecs.INTEGER, Protocol764::x,
				Protocol764::new
			);

		public Protocol764() {
			super();
		}

		public Protocol764(int z, int x) {
			super(z, x);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 764;
		}
	}
}

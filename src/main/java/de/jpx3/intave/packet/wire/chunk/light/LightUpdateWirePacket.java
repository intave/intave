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

package de.jpx3.intave.packet.wire.chunk.light;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableLightUpdatePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.ChunkLightData;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LightUpdateWirePacket {
	private LightUpdateWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol477.class,
			Protocol735.class,
			Protocol755.class,
			Protocol763.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableLightUpdatePacket {
		protected int x, z;
		protected ChunkLightData lightData;
		protected Base() { this(0, 0, new ChunkLightData()); }

		protected Base(int x, int z, ChunkLightData lightData) {
			this.x = x;
			this.z = z;
			this.lightData = Objects.requireNonNull(lightData, "lightData");
		}

		public int x() { return x; }

		public int z() { return z; }

		@Override
		public int chunkX(int index) { checkIndex(index); return x; }

		@Override
		public int chunkZ(int index) { checkIndex(index); return z; }

		@Override
		public ChunkLightData lightData() { return lightData; }

		@Override
		public void setChunkCoordinates(int index, int x, int z) {
			markMutated();
			checkIndex(index);
			this.x = x;
			this.z = z;
		}

		@Override
		public void setLightData(ChunkLightData lightData) {
			markMutated();
			this.lightData = Objects.requireNonNull(lightData, "lightData");
		}

		protected static void checkIndex(int index) {
			if (index != 0) throw new IndexOutOfBoundsException("Chunk index: " + index);
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Base::x,
			ByteBufStreamCodecs.VARINT, Base::z,
			ChunkLightData.LEGACY_STREAM_CODEC, Base::lightData,
			Protocol477::new
		);

		public Protocol477() { super(); }
		public Protocol477(int x, int z, ChunkLightData lightData) {
			super(x, z, lightData);
		}

		@Override
		public boolean appliesTo(int protocolVersion) { return protocolVersion >= 477 && protocolVersion < 735; }
	}

	static final class Protocol735 extends LightUpdateWirePacket.Protocol755 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol735> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Base::x,
			ByteBufStreamCodecs.VARINT, Base::z,
			ByteBufStreamCodecs.BOOLEAN, LightUpdateWirePacket.Protocol755::trustEdges,
			ChunkLightData.LEGACY_STREAM_CODEC, Base::lightData,
			Protocol735::new
		);

		public Protocol735() { super(); }
		public Protocol735(int x, int z, boolean trustEdges, ChunkLightData lightData) {
			super(x, z, trustEdges, lightData);
		}

		@Override
		public boolean appliesTo(int protocolVersion) { return protocolVersion >= 735 && protocolVersion < 755; }
	}

	static class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Base::x,
			ByteBufStreamCodecs.VARINT, Base::z,
			ByteBufStreamCodecs.BOOLEAN, Protocol755::trustEdges,
			ChunkLightData.STREAM_CODEC, Base::lightData,
			Protocol755::new
		);
		private boolean trustEdges;

		public Protocol755() { super(); }
		public Protocol755(int x, int z, boolean trustEdges, ChunkLightData lightData) {
			super(x, z, lightData);
			this.trustEdges = trustEdges;
		}

		@Override
		public boolean appliesTo(int protocolVersion) { return protocolVersion >= 755 && protocolVersion < 763; }

		public boolean trustEdges() { return trustEdges; }
		public void setTrustEdges(boolean trustEdges) {
			markMutated();
			this.trustEdges = trustEdges;
		}
	}

	static final class Protocol763 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol763> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol763::x,
			ByteBufStreamCodecs.VARINT, Protocol763::z,
			ChunkLightData.STREAM_CODEC, Protocol763::lightData,
			Protocol763::new
		);

		public Protocol763() {
			super();
		}

		public Protocol763(int x, int z, ChunkLightData lightData) {
			super(x, z, lightData);
		}

		@Override
		public boolean appliesTo(int protocolVersion) { return protocolVersion >= 763; }
	}
}

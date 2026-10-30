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

package de.jpx3.intave.packet.wire.chunk.bulk;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableMapChunkBulkPacket;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.packet.wire.WireCodecs.bytes;
import static de.jpx3.intave.packet.wire.WireCodecs.count;

public class MapChunkBulkWirePacket implements MutableMapChunkBulkPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MapChunkBulkWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						output.writeBoolean(packet.skyLight);
						ByteBufStreamCodecs.VARINT.encode(output, packet.x.length);
						for (int i = 0; i < packet.x.length; i++) {
							output.writeInt(packet.x[i]);
							output.writeInt(packet.z[i]);
							output.writeShort(packet.masks[i]);
						}
						output.writeBytes(packet.data);
					},
					input -> {
						boolean skyLight = input.readBoolean();
						int count = count(input, input.readableBytes() / 10);
						int[] x = new int[count];
						int[] z = new int[count];
						int[] masks = new int[count];
						for (int i = 0; i < count; i++) {
							x[i] = input.readInt();
							z[i] = input.readInt();
							masks[i] = input.readUnsignedShort();
						}
						byte[] data = bytes(input, input.readableBytes());
						return new MapChunkBulkWirePacket(skyLight, x, z, masks, data);
					});

	private boolean skyLight;

	private int[] x = new int[0], z = new int[0], masks = new int[0];

	private byte[] data = new byte[0];

	public MapChunkBulkWirePacket() {
		this(false, new int[0], new int[0], new int[0], new byte[0]);
	}

	public MapChunkBulkWirePacket(boolean skyLight, int[] x, int[] z, int[] masks, byte[] data) {
		this.skyLight = skyLight;
		this.x = x;
		this.z = z;
		this.masks = masks;
		this.data = data;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("MAP_CHUNK_BULK".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}

	@Override
	public int chunkCount() {
		return x.length;
	}

	@Override
	public int chunkX(int index) {
		return x[index];
	}

	@Override
	public int chunkZ(int index) {
		return z[index];
	}

	@Override
	public void setChunkCoordinates(int index, int x, int z) {
		this.x[index] = x;
		this.z[index] = z;
	}
}

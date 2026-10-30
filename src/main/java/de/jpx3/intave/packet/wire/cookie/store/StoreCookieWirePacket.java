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

package de.jpx3.intave.packet.wire.cookie.store;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableStoreCookiePacket;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.packet.wire.WireCodecs.bytes;
import static de.jpx3.intave.packet.wire.WireCodecs.count;

public class StoreCookieWirePacket implements MutableStoreCookiePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, StoreCookieWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						MinecraftKey.STREAM_CODEC.encode(output, packet.key);
						{
							if (packet.payload.length > 5120)
								throw new IllegalArgumentException("Cookie exceeds 5120 bytes");
							ByteBufStreamCodecs.VARINT.encode(output, packet.payload.length);
							output.writeBytes(packet.payload);
						}
					},
					input -> {
						MinecraftKey key = MinecraftKey.STREAM_CODEC.decode(input);
						byte[] payload = bytes(input, count(input, 5120));
						return new StoreCookieWirePacket(key, payload);
					});

	protected MinecraftKey key = MinecraftKey.withDefaultNamespace("cookie");

	protected byte[] payload = new byte[0];

	public StoreCookieWirePacket() {
		this(MinecraftKey.withDefaultNamespace("cookie"), new byte[0]);
	}

	public StoreCookieWirePacket(MinecraftKey key, byte[] payload) {
		this.key = key;
		this.payload = payload;
	}

	@Override
	public MinecraftKey key() {
		return this.key;
	}

	@Override
	public void setKey(MinecraftKey value) {
		this.key = value;
	}

	@Override
	public byte[] payload() {
		return this.payload;
	}

	@Override
	public void setPayload(byte[] value) {
		this.payload = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.LOGIN
						|| state == ConnectionState.CONFIGURATION
						|| state == ConnectionState.PLAY)
				&& ("STORE_COOKIE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 766;
	}
}

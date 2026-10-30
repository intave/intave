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

package de.jpx3.intave.packet.wire.cookie.response;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableCookieResponsePacket;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

public final class CookieResponseWirePacket implements MutableCookieResponsePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, CookieResponseWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			MinecraftKey.STREAM_CODEC, CookieResponseWirePacket::key,
			ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.varIntByteArray(5120)), CookieResponseWirePacket::payload,
			CookieResponseWirePacket::new
		);

	private MinecraftKey key;
	private byte[] payload;

	public CookieResponseWirePacket() {
		this(MinecraftKey.withDefaultNamespace("cookie"), null);
	}

	public CookieResponseWirePacket(MinecraftKey key, byte[] payload) {
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
				&& ("COOKIE_RESPONSE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 766;
	}
}

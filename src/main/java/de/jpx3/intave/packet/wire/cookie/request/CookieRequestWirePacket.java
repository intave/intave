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

package de.jpx3.intave.packet.wire.cookie.request;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableCookieRequestPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

public class CookieRequestWirePacket extends MutableWirePacket implements MutableCookieRequestPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, CookieRequestWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			MinecraftKey.STREAM_CODEC, CookieRequestWirePacket::key,
			CookieRequestWirePacket::new
		);

	protected MinecraftKey key;

	public CookieRequestWirePacket() {
		this(MinecraftKey.withDefaultNamespace("cookie"));
	}

	public CookieRequestWirePacket(MinecraftKey key) {
		this.key = key;
	}

	@Override
	public MinecraftKey key() {
		return this.key;
	}

	@Override
	public void setKey(MinecraftKey value) {
		markMutated();
		this.key = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 766;
	}
}

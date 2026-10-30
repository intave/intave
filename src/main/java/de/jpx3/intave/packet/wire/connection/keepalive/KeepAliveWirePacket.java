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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableKeepAlivePacket;
import io.netty.buffer.ByteBuf;

public class KeepAliveWirePacket implements MutableKeepAlivePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, KeepAliveWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.LONG, KeepAliveWirePacket::id,
			KeepAliveWirePacket::new
		);

	protected long id = 0;

	public KeepAliveWirePacket() {
		this(0);
	}

	public KeepAliveWirePacket(long id) {
		this.id = id;
	}

	@Override
	public long id() {
		return this.id;
	}

	@Override
	public void setId(long value) {
		this.id = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY || state == ConnectionState.CONFIGURATION)
				&& ("KEEP_ALIVE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 340;
	}
}

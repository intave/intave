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

package de.jpx3.intave.packet.wire.connection.ping;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePingPacket;
import io.netty.buffer.ByteBuf;

public class PingWirePacket implements MutablePingPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PingWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER, PingWirePacket::id,
			PingWirePacket::new
		);

	protected int id = 0;

	public PingWirePacket() {
		this(0);
	}

	public PingWirePacket(int id) {
		this.id = id;
	}

	@Override
	public int id() {
		return this.id;
	}

	@Override
	public void setId(int value) {
		this.id = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY || state == ConnectionState.CONFIGURATION)
				&& ("PING".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755;
	}
}

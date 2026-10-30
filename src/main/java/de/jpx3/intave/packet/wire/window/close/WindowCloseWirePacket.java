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

package de.jpx3.intave.packet.wire.window.close;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableWindowClosePacket;
import io.netty.buffer.ByteBuf;

public class WindowCloseWirePacket implements MutableWindowClosePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WindowCloseWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, WindowCloseWirePacket::containerId,
			WindowCloseWirePacket::new
		);

	protected int containerId = 0;

	public WindowCloseWirePacket() {
		this(0);
	}

	public WindowCloseWirePacket(int containerId) {
		this.containerId = containerId;
	}

	@Override
	public int containerId() {
		return this.containerId;
	}

	@Override
	public void setContainerId(int value) {
		this.containerId = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("CLOSE_WINDOW".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}
}

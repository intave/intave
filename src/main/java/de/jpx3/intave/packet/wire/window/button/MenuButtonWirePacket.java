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

package de.jpx3.intave.packet.wire.window.button;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableMenuButtonPacket;
import io.netty.buffer.ByteBuf;

public class MenuButtonWirePacket implements MutableMenuButtonPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MenuButtonWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, MenuButtonWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, MenuButtonWirePacket::button,
			MenuButtonWirePacket::new
		);

	protected int containerId = 0;

	protected int button = 0;

	public MenuButtonWirePacket() {
		this(0, 0);
	}

	public MenuButtonWirePacket(int containerId, int button) {
		this.containerId = containerId;
		this.button = button;
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
	public int button() {
		return this.button;
	}

	@Override
	public void setButton(int value) {
		this.button = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ENCHANT_ITEM".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 766;
	}
}

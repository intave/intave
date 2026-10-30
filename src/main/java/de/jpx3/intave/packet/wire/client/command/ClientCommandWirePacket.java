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

package de.jpx3.intave.packet.wire.client.command;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableClientCommandPacket;
import de.jpx3.intave.share.ClientCommand;
import io.netty.buffer.ByteBuf;

public class ClientCommandWirePacket implements MutableClientCommandPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ClientCommandWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.enumCodec(ClientCommand.values()), ClientCommandWirePacket::command,
			ClientCommandWirePacket::new
		);

	protected ClientCommand command = ClientCommand.PERFORM_RESPAWN;

	public ClientCommandWirePacket() {
		this(ClientCommand.PERFORM_RESPAWN);
	}

	public ClientCommandWirePacket(ClientCommand command) {
		this.command = command;
	}

	@Override
	public ClientCommand command() {
		return this.command;
	}

	@Override
	public void setCommand(ClientCommand value) {
		this.command = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("CLIENT_COMMAND".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}

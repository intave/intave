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

package de.jpx3.intave.packet.wire.player.teleportaccept;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableTeleportAcceptPacket;
import de.jpx3.intave.share.PositionAndRotation;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public class TeleportAcceptWirePacket implements MutableTeleportAcceptPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TeleportAcceptWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.teleportId);
						PositionAndRotation.STREAM_CODEC.encode(
								output,
								Objects.requireNonNull(
										packet.positionAndRotation, "positionAndRotation"));
					},
					input -> {
						int teleportId = ByteBufStreamCodecs.VARINT.decode(input);
						PositionAndRotation positionAndRotation =
								PositionAndRotation.STREAM_CODEC.decode(input);
						return new TeleportAcceptWirePacket(teleportId, positionAndRotation);
					});

	protected int teleportId = 0;

	protected PositionAndRotation positionAndRotation;

	public TeleportAcceptWirePacket() {
		this(0, null);
	}

	public TeleportAcceptWirePacket(int teleportId, PositionAndRotation positionAndRotation) {
		this.teleportId = teleportId;
		this.positionAndRotation = positionAndRotation;
	}

	@Override
	public int teleportId() {
		return this.teleportId;
	}

	@Override
	public void setTeleportId(int value) {
		this.teleportId = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("TELEPORT_ACCEPT".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 777;
	}

	@Override
	public PositionAndRotation positionAndRotation() {
		return positionAndRotation;
	}

	@Override
	public void setPositionAndRotation(PositionAndRotation value) {
		positionAndRotation = value;
	}
}

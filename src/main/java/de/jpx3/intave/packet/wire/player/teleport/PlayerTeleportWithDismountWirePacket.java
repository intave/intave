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

package de.jpx3.intave.packet.wire.player.teleport;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.EnumSet;
import java.util.Set;

public class PlayerTeleportWithDismountWirePacket extends PlayerTeleportWithIdWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerTeleportWithDismountWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Position.STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::position,
			Rotation.STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::rotation,
			Relative.LEGACY_FLAGS_STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::flags,
			ByteBufStreamCodecs.VARINT, PlayerTeleportWithIdWirePacket::teleportIdValue,
			ByteBufStreamCodecs.BOOLEAN, PlayerTeleportWithDismountWirePacket::dismount,
			PlayerTeleportWithDismountWirePacket::new
		);

	protected boolean dismount;

	public PlayerTeleportWithDismountWirePacket() {
		this(new Position(), new Rotation(0, 0), EnumSet.noneOf(Relative.class), 0, false);
	}

	public PlayerTeleportWithDismountWirePacket(
			Position position,
			Rotation rotation,
			Set<Relative> flags,
			int teleportId,
			boolean dismount) {
		super(position, rotation, flags, teleportId);
		this.dismount = dismount;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755 && protocolVersion < 762;
	}

	public boolean dismount() {
		return dismount;
	}
}

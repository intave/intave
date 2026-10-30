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
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.Set;

public class PlayerTeleportWirePacket extends PlayerTeleportWithIdWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerTeleportWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, PlayerTeleportWithIdWirePacket::teleportIdValue,
			Position.STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::position,
			Motion.STREAM_CODEC, PlayerTeleportWirePacket::motion,
			Rotation.STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::rotation,
			Relative.FLAGS_STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::flags,
			PlayerTeleportWirePacket::new
		);

	protected Motion motion;

	public PlayerTeleportWirePacket() {
		this(
				0,
				new Position(),
				new Motion(0, 0, 0),
				new Rotation(0, 0),
				EnumSet.noneOf(Relative.class));
	}

	public PlayerTeleportWirePacket(
			int teleportId,
			Position position,
			Motion motion,
			Rotation rotation,
			Set<Relative> flags) {
		super(position, rotation, flags, teleportId);
		this.motion = motion;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}

	@Override
	public OptionalInt teleportId() {
		return OptionalInt.of(teleportId);
	}

	@Override
	public void setTeleportId(int value) {
		teleportId = value;
	}

	@Override
	public Motion motion() {
		return motion;
	}

	@Override
	public void setMotion(Motion value) {
		motion = value;
	}
}

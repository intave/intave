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

package de.jpx3.intave.packet.wire.entity.position;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.EnumSet;
import java.util.Set;

public final class EntityPositionWirePacket extends EntityPositionDoublePositionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityPositionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityPositionDoublePositionWirePacket::entityId,
			Position.STREAM_CODEC, EntityPositionDoublePositionWirePacket::position,
			Motion.STREAM_CODEC, EntityPositionWirePacket::motion,
			Rotation.STREAM_CODEC, EntityPositionDoublePositionWirePacket::rotation,
			Relative.FLAGS_STREAM_CODEC, EntityPositionWirePacket::flags,
			ByteBufStreamCodecs.BOOLEAN, EntityPositionDoublePositionWirePacket::onGround,
			EntityPositionWirePacket::new
		);

	private Motion motion;
	private Set<Relative> flags;

	public EntityPositionWirePacket() {
		this(0, new Position(), Motion.newEmpty(), Rotation.zero(), EnumSet.noneOf(Relative.class), false);
	}

	public EntityPositionWirePacket(
		int entityId, Position position, Motion motion, Rotation rotation, Set<Relative> flags, boolean onGround
	) {
		super(entityId, position, rotation, onGround);
		this.motion = motion;
		this.flags = flags;
	}

	public Motion motion() {
		return motion;
	}

	public void setMotion(Motion value) {
		motion = value;
	}

	public Set<Relative> flags() {
		return flags;
	}

	public void setFlags(Set<Relative> value) {
		flags = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 768;
	}
}

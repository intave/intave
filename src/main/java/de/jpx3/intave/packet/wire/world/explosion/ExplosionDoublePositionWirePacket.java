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

package de.jpx3.intave.packet.wire.world.explosion;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;

import java.util.List;

public final class ExplosionDoublePositionWirePacket extends ExplosionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ExplosionDoublePositionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.DOUBLE, ExplosionWirePacket::x,
			ByteBufStreamCodecs.DOUBLE, ExplosionWirePacket::y,
			ByteBufStreamCodecs.DOUBLE, ExplosionWirePacket::z,
			ByteBufStreamCodecs.FLOAT, ExplosionWirePacket::power,
			VAR_INT_BLOCK_OFFSETS, ExplosionWirePacket::affectedBlockOffsets,
			Motion.FLOAT_STREAM_CODEC, ExplosionWirePacket::motion,
			ExplosionDoublePositionWirePacket::new
		);

	public ExplosionDoublePositionWirePacket() {
		super();
	}

	public ExplosionDoublePositionWirePacket(
			double x,
			double y,
			double z,
			float power,
			List<BlockPosition> affectedBlockOffsets,
			Motion motion) {
		super(x, y, z, power, affectedBlockOffsets, motion);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 761 && protocolVersion < 765;
	}
}

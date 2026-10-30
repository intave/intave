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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableExplosionPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class ExplosionWirePacket implements MutableExplosionPacket {
	protected static final StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> INT_BLOCK_OFFSETS =
			blockOffsetsCodec(ByteBufStreamCodecs.INTEGER);

	protected static final StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>>
			VAR_INT_BLOCK_OFFSETS = blockOffsetsCodec(ByteBufStreamCodecs.VARINT);

	protected double x, y, z;

	protected float power;

	protected List<BlockPosition> affectedBlockOffsets = List.of();

	protected Motion motion = new Motion(0, 0, 0);

	public ExplosionWirePacket() {
		this(0, 0, 0, 0, List.of(), new Motion(0, 0, 0));
	}

	public ExplosionWirePacket(
			double x,
			double y,
			double z,
			float power,
			List<BlockPosition> affectedBlockOffsets,
			Motion motion) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.power = power;
		this.affectedBlockOffsets = affectedBlockOffsets;
		this.motion = motion;
	}

	public double x() {
		return x;
	}

	public double y() {
		return y;
	}

	public double z() {
		return z;
	}

	public void setPosition(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public float power() {
		return power;
	}

	public void setPower(float power) {
		this.power = power;
	}

	public List<BlockPosition> affectedBlockOffsets() {
		return affectedBlockOffsets;
	}

	public void setAffectedBlockOffsets(List<BlockPosition> offsets) {
		this.affectedBlockOffsets = List.copyOf(offsets);
	}

	@Override
	public Motion motion() {
		return this.motion;
	}

	@Override
	public void setMotion(Motion value) {
		this.motion = Objects.requireNonNull(value, "motion");
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("EXPLOSION".equals(name));
	}

	@Override
	public abstract boolean appliesTo(int protocolVersion);

	private static StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> blockOffsetsCodec(
			StreamCodec<ByteBuf, ByteBuf, Integer> countCodec) {
		return StreamCodec.of(
				(output, offsets) -> {
					countCodec.encode(output, offsets.size());
					for (BlockPosition offset : offsets) {
						output.writeByte(checkedOffset(offset.getX()));
						output.writeByte(checkedOffset(offset.getY()));
						output.writeByte(checkedOffset(offset.getZ()));
					}
				},
				input -> {
					int count = countCodec.decode(input);
					// Three bytes per block, followed by three floats for the knockback.
					if (count < 0
							|| input.readableBytes() < 12
							|| count > (input.readableBytes() - 12) / 3) {
						throw new IllegalArgumentException("Invalid explosion block count");
					}
					List<BlockPosition> offsets = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						offsets.add(
								new BlockPosition(
										input.readByte(), input.readByte(), input.readByte()));
					}
					return List.copyOf(offsets);
				});
	}

	private static int checkedOffset(int offset) {
		if (offset < Byte.MIN_VALUE || offset > Byte.MAX_VALUE) {
			throw new IllegalArgumentException(
					"Explosion block offset is outside the signed byte range");
		}
		return offset;
	}
}

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
import de.jpx3.intave.packet.contract.mutable.MutableExplosionPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class ExplosionWirePacket extends AbstractMutable implements MutableExplosionPacket {
	protected static final StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> INT_BLOCK_OFFSETS = blockOffsetsCodec(ByteBufStreamCodecs.INTEGER);
	protected static final StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> VAR_INT_BLOCK_OFFSETS = blockOffsetsCodec(ByteBufStreamCodecs.VARINT);

	protected double x, y, z;
	protected float power;
	protected List<BlockPosition> affectedBlockOffsets;
	protected Motion motion;
	protected byte[] effects = new byte[0];

	public ExplosionWirePacket() {
		this(0, 0, 0, 0, new ArrayList<>(), new Motion(0, 0, 0));
	}

	public ExplosionWirePacket(
		double x, double y, double z, float power,
		List<BlockPosition> affectedBlockOffsets, Motion motion
	) {
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
		markMutated();
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public float power() {
		return power;
	}

	public void setPower(float power) {
		markMutated();
		this.power = power;
	}

	public void setEffects(byte[] effects) {
		markMutated();
		this.effects = effects.clone();
	}

	/** Cloud particles, a direct generic explosion sound and no weighted block particles. */
	public void setDefaultEffects(int protocol) {
		markMutated();
		if (!appliesTo(protocol) || protocol > 777) {
			throw new IllegalArgumentException("Unsupported explosion effects protocol: " + protocol);
		}
		if (protocol < 765) return;
		ByteBuf output = Unpooled.buffer();
		try {
			// AMBIENT_ENTITY_EFFECT was removed in 1.20.5; sulfur particles precede CLOUD in 26.2.
			int cloudParticleId = protocol == 765 ? 5 : protocol >= 776 ? 11 : 4;
			if (protocol < 768) {
				ByteBufStreamCodecs.VARINT.encode(output, 0); // KEEP block interaction
				ByteBufStreamCodecs.VARINT.encode(output, cloudParticleId); // small CLOUD particle
			}
			ByteBufStreamCodecs.VARINT.encode(output, cloudParticleId);
			if (protocol >= 766) ByteBufStreamCodecs.VARINT.encode(output, 0); // direct SoundEvent holder
			ByteBufStreamCodecs.VARINT_STRING.encode(output, "minecraft:entity.generic.explode");
			output.writeBoolean(false); // no fixed sound range
			if (protocol >= 773) ByteBufStreamCodecs.VARINT.encode(output, 0); // empty WeightedList
			if (protocol >= 777) output.writeBoolean(true); // playSound
			byte[] payload = new byte[output.readableBytes()];
			output.readBytes(payload);
			setEffects(payload);
		} finally {
			output.release();
		}
	}

	public void setSilentDefaults(int protocol) {
		setPosition(0, -1.0E9D, 0);
		setPower(0);
		setAffectedBlockOffsets(new ArrayList<>());
		setDefaultEffects(protocol);
		if (protocol >= 777) effects[effects.length - 1] = 0;
	}

	public List<BlockPosition> affectedBlockOffsets() {
		markMutated();
		return affectedBlockOffsets;
	}

	public void setAffectedBlockOffsets(List<BlockPosition> offsets) {
		markMutated();
		this.affectedBlockOffsets = new ArrayList<>(offsets);
	}

	@Override
	public Motion motion() {
		markMutated();
		return this.motion;
	}

	@Override
	public void setMotion(Motion value) {
		markMutated();
		this.motion = Objects.requireNonNull(value.copy(), "motion");
	}

	@Override
	public abstract boolean appliesTo(int protocolVersion);

	private static StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> blockOffsetsCodec(
		StreamCodec<ByteBuf, ByteBuf, Integer> countCodec
	) {
		return StreamCodec.of((output, offsets) -> {
			countCodec.encode(output, offsets.size());
			for (BlockPosition offset : offsets) {
				output.writeByte(checkedOffset(offset.getX()));
				output.writeByte(checkedOffset(offset.getY()));
				output.writeByte(checkedOffset(offset.getZ()));
			}
		}, input -> {
			int count = countCodec.decode(input);
			// Three bytes per block, followed by three floats for the knockback.
			if (count < 0 || input.readableBytes() < 12 || count > (input.readableBytes() - 12) / 3) {
				throw new IllegalArgumentException("Invalid explosion block count");
			}
			List<BlockPosition> offsets = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				offsets.add(new BlockPosition(input.readByte(), input.readByte(), input.readByte()));
			}
			return new ArrayList<>(offsets);
		});
	}

	private static int checkedOffset(int offset) {
		if (offset < Byte.MIN_VALUE || offset > Byte.MAX_VALUE) {
			throw new IllegalArgumentException("Explosion block offset is outside the signed byte range");
		}
		return offset;
	}
}

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

import java.util.*;

public final class ExplosionWirePacket {
	private ExplosionWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol755.class,
			Protocol761.class,
			Protocol765.class,
			Protocol768.class,
			Protocol773.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableExplosionPacket {
		protected static final StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> INT_BLOCK_OFFSETS = blockOffsetsCodec(ByteBufStreamCodecs.INTEGER);

		protected static final StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> VAR_INT_BLOCK_OFFSETS = blockOffsetsCodec(ByteBufStreamCodecs.VARINT);

		protected double x, y, z;
		protected float power;
		protected List<BlockPosition> affectedBlockOffsets;
		protected Motion motion;
		protected byte[] effects = new byte[0];

		protected Base() {
			this(0, 0, 0, 0, new ArrayList<>(), new Motion(0, 0, 0));
		}

		protected Base(
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

		@Override
		public double x() {
			return x;
		}

		@Override
		public double y() {
			return y;
		}

		@Override
		public double z() {
			return z;
		}

		@Override
		public void setPosition(double x, double y, double z) {
			markMutated();
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public float power() {
			return power;
		}

		@Override
		public void setPower(float power) {
			markMutated();
			this.power = power;
		}

		public void setEffects(byte[] effects) {
			markMutated();
			this.effects = effects.clone();
		}

		/** Cloud particles, a direct generic explosion sound and no weighted block particles. */
		@Override
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

		@Override
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

		protected static StreamCodec<ByteBuf, ByteBuf, List<BlockPosition>> blockOffsetsCodec(
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

		protected static int checkedOffset(int offset) {
			if (offset < Byte.MIN_VALUE || offset > Byte.MAX_VALUE) {
				throw new IllegalArgumentException("Explosion block offset is outside the signed byte range");
			}
			return offset;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Base::x,
				ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Base::y,
				ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Base::z,
				ByteBufStreamCodecs.FLOAT, Base::power,
				INT_BLOCK_OFFSETS, Base::affectedBlockOffsets,
				Motion.FLOAT_STREAM_CODEC, Base::motion,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(
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
			return protocolVersion >= 47 && protocolVersion < 755;
		}
	}

	static final class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Base::x,
				ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Base::y,
				ByteBufStreamCodecs.FLOAT_AS_DOUBLE, Base::z,
				ByteBufStreamCodecs.FLOAT, Base::power,
				VAR_INT_BLOCK_OFFSETS, Base::affectedBlockOffsets,
				Motion.FLOAT_STREAM_CODEC, Base::motion,
				Protocol755::new
			);

		public Protocol755() {
			super();
		}

		public Protocol755(
			double x, double y, double z,
			float power, List<BlockPosition> affectedBlockOffsets,
			Motion motion
		) {
			super(x, y, z, power, affectedBlockOffsets, motion);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 755 && protocolVersion < 761;
		}
	}

	static final class Protocol761 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol761> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.DOUBLE, Base::x,
				ByteBufStreamCodecs.DOUBLE, Base::y,
				ByteBufStreamCodecs.DOUBLE, Base::z,
				ByteBufStreamCodecs.FLOAT, Base::power,
				VAR_INT_BLOCK_OFFSETS, Base::affectedBlockOffsets,
				Motion.FLOAT_STREAM_CODEC, Base::motion,
				Protocol761::new
			);

		public Protocol761() {
			super();
		}

		public Protocol761(
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

	static final class Protocol765 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol765> STREAM_CODEC = StreamCodec.of(
			(output, packet) -> {
				output.writeDouble(packet.x).writeDouble(packet.y).writeDouble(packet.z).writeFloat(packet.power);
				VAR_INT_BLOCK_OFFSETS.encode(output, packet.affectedBlockOffsets);
				Motion.FLOAT_STREAM_CODEC.encode(output, packet.motion);
				output.writeBytes(packet.effects);
			}, input -> {
				Protocol765 packet = new Protocol765();
				packet.x = input.readDouble();
				packet.y = input.readDouble();
				packet.z = input.readDouble();
				packet.power = input.readFloat();
				packet.affectedBlockOffsets = VAR_INT_BLOCK_OFFSETS.decode(input);
				packet.motion = Motion.FLOAT_STREAM_CODEC.decode(input);
				// Registry-dependent effect data is unchanged when editing only the motion.
				packet.effects = new byte[input.readableBytes()];
				input.readBytes(packet.effects);
				return packet;
			});

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 765 && protocolVersion < 768;
		}
	}

	static class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.of((output, packet) -> packet.write(output, false), input -> {
				Protocol768 packet = new Protocol768();
				packet.read(input, false);
				return packet;
			});

		protected int blockCount;

		public Protocol768() {
			motion = null;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768 && protocolVersion < 773;
		}

		@Override
		public void setMotion(Motion motion) {
			markMutated();
			this.motion = motion;
		}

		@Override
		public void setSilentDefaults(int protocol) {
			super.setSilentDefaults(protocol);
			blockCount = 0;
		}

		protected final void read(ByteBuf input, boolean hasRadius) {
			x = input.readDouble();
			y = input.readDouble();
			z = input.readDouble();
			if (hasRadius) {
				power = input.readFloat();
				blockCount = input.readInt();
			}
			motion = input.readBoolean() ? Motion.STREAM_CODEC.decode(input) : null;
			// Keep the version-specific particle, sound, weighted particles and playSound payload intact.
			effects = new byte[input.readableBytes()];
			input.readBytes(effects);
		}

		protected final void write(ByteBuf output, boolean hasRadius) {
			output.writeDouble(x).writeDouble(y).writeDouble(z);
			if (hasRadius) output.writeFloat(power).writeInt(blockCount);
			output.writeBoolean(motion != null);
			if (motion != null) Motion.STREAM_CODEC.encode(output, motion);
			output.writeBytes(effects);
		}
	}

	static final class Protocol773 extends ExplosionWirePacket.Protocol768 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol773> STREAM_CODEC =
			StreamCodec.of((output, packet) -> packet.write(output, true), input -> {
				Protocol773 packet = new Protocol773();
				packet.read(input, true);
				return packet;
			});

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 773;
		}
	}
}

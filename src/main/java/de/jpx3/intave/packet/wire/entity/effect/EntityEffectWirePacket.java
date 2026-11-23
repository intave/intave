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

package de.jpx3.intave.packet.wire.entity.effect;

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableEntityEffectPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class EntityEffectWirePacket {
	private EntityEffectWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class, Protocol758.class, Protocol759.class, Protocol764.class, Protocol766.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityEffectPacket {
		private int entityId;
		private MinecraftKey effectType;
		private int amplifier;
		private int duration;
		private final byte flags;

		Base() {
			this(0, MinecraftKey.withDefaultNamespace("speed"), 0, 0, (byte) 0);
		}

		Base(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags) {
			this.entityId = entityId;
			this.effectType = Objects.requireNonNull(effectType);
			this.amplifier = amplifier;
			this.duration = duration;
			this.flags = flags;
		}

		@Override
		public int entityId() {
			return entityId;
		}

		@Override
		public void setEntityId(int entityId) {
			markMutated();
			this.entityId = entityId;
		}

		@Override
		public MinecraftKey effectType() {
			return effectType;
		}

		@Override
		public void setEffectType(MinecraftKey effectType) {
			markMutated();
			this.effectType = Objects.requireNonNull(effectType);
		}

		@Override
		public int effectAmplifier() {
			return amplifier;
		}

		@Override
		public void setEffectAmplifier(int amplifier) {
			markMutated();
			this.amplifier = amplifier;
		}

		@Override
		public int effectDuration() {
			return duration;
		}

		@Override
		public void setEffectDuration(int duration) {
			markMutated();
			this.duration = duration;
		}

		public byte flags() {
			return flags;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol47::entityId,
			EffectTypeRegistry.LEGACY.codec(ByteBufStreamCodecs.UNSIGNED_BYTE), Protocol47::effectType,
			ByteBufStreamCodecs.SIGNED_BYTE, Protocol47::effectAmplifier,
			ByteBufStreamCodecs.VARINT, Protocol47::effectDuration,
			ByteBufStreamCodecs.BYTE, Protocol47::flags,
			Protocol47::new
		);

		public Protocol47() {
		}

		Protocol47(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags) {
			super(entityId, effectType, amplifier, duration, flags);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 758;
		}
	}

	static final class Protocol758 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol758> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol758::entityId,
			EffectTypeRegistry.LEGACY.codec(ByteBufStreamCodecs.VARINT), Protocol758::effectType,
			ByteBufStreamCodecs.SIGNED_BYTE, Protocol758::effectAmplifier,
			ByteBufStreamCodecs.VARINT, Protocol758::effectDuration,
			ByteBufStreamCodecs.BYTE, Protocol758::flags,
			Protocol758::new
		);

		public Protocol758() {
		}

		Protocol758(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags) {
			super(entityId, effectType, amplifier, duration, flags);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion == 758;
		}
	}

	static abstract class WithFactorData extends Base {
		private final CompoundTag factorData;

		WithFactorData() {
			factorData = null;
		}

		WithFactorData(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags, CompoundTag factorData) {
			super(entityId, effectType, amplifier, duration, flags);
			this.factorData = factorData;
		}

		public CompoundTag factorData() {
			return factorData;
		}
	}

	static final class Protocol759 extends WithFactorData {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol759> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol759::entityId,
			EffectTypeRegistry.LEGACY.codec(ByteBufStreamCodecs.VARINT), Protocol759::effectType,
			ByteBufStreamCodecs.SIGNED_BYTE, Protocol759::effectAmplifier,
			ByteBufStreamCodecs.VARINT, Protocol759::effectDuration,
			ByteBufStreamCodecs.BYTE, Protocol759::flags,
			ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.NAMED_COMPOUND_TAG), Protocol759::factorData,
			Protocol759::new
		);

		public Protocol759() {
		}

		Protocol759(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags, CompoundTag factorData) {
			super(entityId, effectType, amplifier, duration, flags, factorData);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 759 && protocolVersion < 764;
		}
	}

	static final class Protocol764 extends WithFactorData {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol764::entityId,
			EffectTypeRegistry.MODERN.codec(ByteBufStreamCodecs.VARINT), Protocol764::effectType,
			ByteBufStreamCodecs.SIGNED_BYTE, Protocol764::effectAmplifier,
			ByteBufStreamCodecs.VARINT, Protocol764::effectDuration,
			ByteBufStreamCodecs.BYTE, Protocol764::flags,
			ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.COMPOUND_TAG), Protocol764::factorData,
			Protocol764::new
		);

		public Protocol764() {
		}

		Protocol764(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags, CompoundTag factorData) {
			super(entityId, effectType, amplifier, duration, flags, factorData);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 764 && protocolVersion < 766;
		}
	}

	static final class Protocol766 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol766> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol766::entityId,
			EffectTypeRegistry.MODERN.codec(ByteBufStreamCodecs.VARINT), Protocol766::effectType,
			ByteBufStreamCodecs.VARINT, Protocol766::effectAmplifier,
			ByteBufStreamCodecs.VARINT, Protocol766::effectDuration,
			ByteBufStreamCodecs.BYTE, Protocol766::flags,
			Protocol766::new
		);

		public Protocol766() {
		}

		Protocol766(int entityId, MinecraftKey effectType, int amplifier, int duration, byte flags) {
			super(entityId, effectType, amplifier, duration, flags);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 766;
		}
	}
}

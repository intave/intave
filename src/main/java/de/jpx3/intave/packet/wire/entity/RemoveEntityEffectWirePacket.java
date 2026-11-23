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

package de.jpx3.intave.packet.wire.entity;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableRemoveEntityEffectPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RemoveEntityEffectWirePacket {
	private RemoveEntityEffectWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(Protocol47.class, Protocol758.class, Protocol764.class));
	}

	static abstract class Base extends AbstractMutable implements MutableRemoveEntityEffectPacket {
		private int entityId;
		private MinecraftKey effectType;

		Base() {
			this(0, MinecraftKey.withDefaultNamespace("speed"));
		}

		Base(int entityId, MinecraftKey effectType) {
			this.entityId = entityId;
			this.effectType = Objects.requireNonNull(effectType);
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
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol47::entityId,
			EffectTypeRegistry.LEGACY.codec(ByteBufStreamCodecs.UNSIGNED_BYTE), Protocol47::effectType,
			Protocol47::new
		);

		public Protocol47() {
		}

		Protocol47(int entityId, MinecraftKey effectType) {
			super(entityId, effectType);
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
			Protocol758::new
		);

		public Protocol758() {
		}

		Protocol758(int entityId, MinecraftKey effectType) {
			super(entityId, effectType);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 758 && protocolVersion < 764;
		}
	}

	static final class Protocol764 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol764> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, Protocol764::entityId,
			EffectTypeRegistry.MODERN.codec(ByteBufStreamCodecs.VARINT), Protocol764::effectType,
			Protocol764::new
		);

		public Protocol764() {
		}

		Protocol764(int entityId, MinecraftKey effectType) {
			super(entityId, effectType);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 764;
		}
	}
}

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

package de.jpx3.intave.packet.wire.window;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableWindowOpenPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

public final class HorseWindowOpenWirePacket {
	private HorseWindowOpenWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol477.class,
			Protocol767.class,
			Protocol768.class,
			Protocol770.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableWindowOpenPacket {
		protected int containerId;
		protected int count, entityId;

		protected Base() {
			this(0, 0, 0);
		}

		protected Base(int containerId, int count, int entityId) {
			this.containerId = containerId;
			this.count = count;
			this.entityId = entityId;
		}

		@Override
		public int containerId() {
			return this.containerId;
		}

		@Override
		public void setContainerId(int value) {
			markMutated();
			this.containerId = value;
		}

		@Override
		public String menuType() {
			return "minecraft:horse";
		}

		@Override
		public void setMenuType(String value) {
			markMutated();
			if (!"minecraft:horse".equals(value))
				throw new IllegalArgumentException("Expected horse menu");
		}

		@Override
		public abstract int slots();

		@Override
		public abstract void setSlots(int value);

		@Override
		public OptionalInt optionalEntityId() {
			return OptionalInt.of(entityId);
		}

		@Override
		public void setEntityId(int value) {
			markMutated();
			entityId = value;
		}

		public int count() {
			return count;
		}

		public int entityId() {
			return entityId;
		}
	}

	static final class Protocol477 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol477> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::containerId,
				ByteBufStreamCodecs.VARINT, Base::count,
				ByteBufStreamCodecs.INTEGER, Base::entityId,
				Protocol477::new
			);

		public Protocol477() {
			super();
		}

		public Protocol477(int containerId, int count, int entityId) {
			super(containerId, count, entityId);
		}

		@Override
		public int slots() {
			return count;
		}

		@Override
		public void setSlots(int value) {
			markMutated();
			int offset = 1;

			count = value;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 477 && protocolVersion < 767;
		}
	}

	static final class Protocol767 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol767> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.UNSIGNED_BYTE, Base::containerId,
				ByteBufStreamCodecs.VARINT, Base::count,
				ByteBufStreamCodecs.INTEGER, Base::entityId,
				Protocol767::new
			);

		public Protocol767() {
			super();
		}

		public Protocol767(int containerId, int count, int entityId) {
			super(containerId, count, entityId);
		}

		@Override
		public int slots() {
			return count * 3 + (1);
		}

		@Override
		public void setSlots(int value) {
			markMutated();
			int offset = 1;
			if (((value < offset || (value - offset) % 3 != 0)))
				throw new IllegalArgumentException("Horse slots must contain complete columns");
			count = (value - offset) / 3;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 767 && protocolVersion < 768;
		}
	}

	static final class Protocol768 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::containerId,
				ByteBufStreamCodecs.VARINT, Base::count,
				ByteBufStreamCodecs.INTEGER, Base::entityId,
				Protocol768::new
			);

		public Protocol768() {
			super();
		}

		public Protocol768(int containerId, int count, int entityId) {
			super(containerId, count, entityId);
		}

		@Override
		public int slots() {
			return count * 3 + (1);
		}

		@Override
		public void setSlots(int value) {
			markMutated();
			int offset = 1;
			if (((value < offset || (value - offset) % 3 != 0)))
				throw new IllegalArgumentException("Horse slots must contain complete columns");
			count = (value - offset) / 3;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768 && protocolVersion < 770;
		}
	}

	static final class Protocol770 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol770> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Protocol770::containerId,
				ByteBufStreamCodecs.VARINT, Protocol770::count,
				ByteBufStreamCodecs.INTEGER, Protocol770::entityId,
				Protocol770::new
			);

		public Protocol770() {
			super();
		}

		public Protocol770(int containerId, int count, int entityId) {
			super(containerId, count, entityId);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 770;
		}

		@Override
		public int slots() {
			return count * 3 + (0);
		}

		@Override
		public void setSlots(int value) {
			markMutated();
			int offset = 0;
			if (((value < offset || (value - offset) % 3 != 0)))
				throw new IllegalArgumentException("Horse slots must contain complete columns");
			count = (value - offset) / 3;
		}
	}
}

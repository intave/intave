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

package de.jpx3.intave.packet.wire.entity.attach;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableAttachEntityPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AttachEntityWirePacket {
	private AttachEntityWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableAttachEntityPacket {
		protected int entityId;
		protected int vehicleId;

		protected Base() {
			this(0, -1);
		}

		protected Base(int entityId, int vehicleId) {
			this.entityId = entityId;
			this.vehicleId = vehicleId;
		}

		@Override
		public int entityId() {
			return this.entityId;
		}

		@Override
		public void setEntityId(int value) {
			markMutated();
			this.entityId = value;
		}

		@Override
		public int vehicleId() {
			return this.vehicleId;
		}

		@Override
		public void setVehicleId(int value) {
			markMutated();
			this.vehicleId = value;
		}

		@Override
		public abstract boolean isMount();

		@Override
		public abstract void setMount(boolean value);
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.INTEGER, Base::entityId,
				ByteBufStreamCodecs.INTEGER, Base::vehicleId,
				ByteBufStreamCodecs.UNSIGNED_BYTE.beforeAndAfter(value -> value == 0, value -> value ? 0 : 1), Protocol47::isMount,
				Protocol47::new
			);

		private boolean mount;

		public Protocol47() {
			super();
		}

		public Protocol47(int entityId, int vehicleId, boolean mount) {
			super(entityId, vehicleId);
			this.mount = mount;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}

		@Override
		public boolean isMount() {
			return mount;
		}

		@Override
		public void setMount(boolean value) {
			markMutated();
			mount = value;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.INTEGER, Protocol107::entityId,
				ByteBufStreamCodecs.INTEGER, Protocol107::vehicleId,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(int entityId, int vehicleId) {
			super(entityId, vehicleId);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107;
		}

		@Override
		public boolean isMount() {
			return false;
		}

		@Override
		public void setMount(boolean value) {
			markMutated();
			if (value) {
				throw new UnsupportedOperationException("Use MOUNT for passenger updates since 1.9");
			}
		}
	}
}

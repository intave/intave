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
import de.jpx3.intave.packet.contract.mutable.MutableEntityDestroyPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.count;

public final class EntityDestroyWirePacket {
	private EntityDestroyWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol755.class,
			Protocol756.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityDestroyPacket {
		protected int[] entityIds;

		protected Base() {
			this(new int[0]);
		}

		protected Base(int[] entityIds) {
			this.entityIds = entityIds;
		}

		@Override
		public int[] entityIds() {
			markMutated();
			return this.entityIds;
		}

		@Override
		public void setEntityIds(int[] value) {
			markMutated();
			this.entityIds = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47>
			STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityIds.length);
					for (int id : packet.entityIds) ByteBufStreamCodecs.VARINT.encode(output, id);
				},
				input -> {
					int[] entityIds = new int[count(input, input.readableBytes())];
					for (int i = 0; i < entityIds.length; i++)
						entityIds[i] = ByteBufStreamCodecs.VARINT.decode(input);
					return new Protocol47(entityIds);
				});

		public Protocol47() {
			super();
		}

		public Protocol47(int[] entityIds) {
			super(entityIds);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 755;
		}
	}

	static final class Protocol755 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755>
			STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					if (packet.entityIds.length != 1)
						throw new IllegalArgumentException("1.17 removes one entity per packet");
					for (int id : packet.entityIds) ByteBufStreamCodecs.VARINT.encode(output, id);
				},
				input -> {
					int[] entityIds = new int[1];
					for (int i = 0; i < entityIds.length; i++)
						entityIds[i] = ByteBufStreamCodecs.VARINT.decode(input);
					return new Protocol755(entityIds);
				});

		public Protocol755() {
			super();
		}

		public Protocol755(int[] entityIds) {
			super(entityIds);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion == 755;
		}
	}

	static final class Protocol756 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol756> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityIds.length);
					for (int id : packet.entityIds) ByteBufStreamCodecs.VARINT.encode(output, id);
				},
				input -> {
					int[] entityIds = new int[count(input, input.readableBytes())];
					for (int i = 0; i < entityIds.length; i++) entityIds[i] = ByteBufStreamCodecs.VARINT.decode(input);
					return new Protocol756(entityIds);
				});

		public Protocol756() {
			super();
		}

		public Protocol756(int[] entityIds) {
			super(entityIds);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 756;
		}
	}
}

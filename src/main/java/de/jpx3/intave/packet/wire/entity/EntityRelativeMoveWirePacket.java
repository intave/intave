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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableEntityRelativeMovePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.EntityRelativeMoveStep;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EntityRelativeMoveWirePacket {
	private EntityRelativeMoveWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableEntityRelativeMovePacket {
		protected int entityId;
		protected boolean onGround, stepped;
		protected List<EntityRelativeMoveStep> steps;

		protected Base() {
			this(
				0,
				false,
				false,
				new ArrayList<>(Collections.singletonList(new Step(new RawVector3d(0, 0, 0), 0))));
		}

		protected Base(
			int entityId, boolean onGround, boolean stepped, List<EntityRelativeMoveStep> steps) {
			this.entityId = entityId;
			this.onGround = onGround;
			this.stepped = stepped;
			this.steps = steps;
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
		public boolean appliesTo(ConnectionState state, PacketType type) {
			return state == ConnectionState.PLAY && type == PacketType.Server.REL_ENTITY_MOVE;
		}

		@Override
		public List<EntityRelativeMoveStep> steps() {
			markMutated();
			return Collections.unmodifiableList(steps);
		}

		@Override
		public abstract void setSteps(List<? extends EntityRelativeMoveStep> value);
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47>
			STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
					EntityRelativeMoveStep step = packet.steps.get(0);
					RawVector3d.BYTE_DELTA_STREAM_CODEC.encode(output, step.delta());

					output.writeBoolean(packet.onGround);
				},
				input -> {
					int entityId = ByteBufStreamCodecs.VARINT.decode(input);
					RawVector3d delta = RawVector3d.BYTE_DELTA_STREAM_CODEC.decode(input);
					List<EntityRelativeMoveStep> steps = new ArrayList<>();
					steps.add(new Step(delta, 0));

					boolean onGround = input.readBoolean();
					return new Protocol47(
						entityId, steps, onGround);
				});

		public Protocol47() {
			super();
		}

		public Protocol47(
			int entityId, List<EntityRelativeMoveStep> steps, boolean onGround) {
			super(entityId, onGround, false, steps);
		}

		@Override
		public void setSteps(List<? extends EntityRelativeMoveStep> value) {
			markMutated();
			if (value.size() != 1 || value.get(0).delayTicks() != 0) {
				throw new IllegalArgumentException("Linear movement requires one step without a delay");
			}
			steps = new ArrayList<>(value);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
					EntityRelativeMoveStep step = packet.steps.get(0);
					RawVector3d.SHORT_DELTA_STREAM_CODEC.encode(output, step.delta());
					output.writeBoolean(packet.onGround);
				},
				input -> {
					int entityId = ByteBufStreamCodecs.VARINT.decode(input);
					RawVector3d delta = RawVector3d.SHORT_DELTA_STREAM_CODEC.decode(input);
					List<EntityRelativeMoveStep> steps = new ArrayList<>();
					steps.add(new Step(delta, 0));
					boolean onGround = input.readBoolean();
					return new Protocol107(
						entityId, steps, onGround);
				});

		public Protocol107() {
			super();
		}

		public Protocol107(
			int entityId, List<EntityRelativeMoveStep> steps, boolean onGround) {
			super(entityId, onGround, false, steps);
		}

		@Override
		public void setSteps(List<? extends EntityRelativeMoveStep> value) {
			markMutated();
			if (value.size() != 1 || value.get(0).delayTicks() != 0) {
				throw new IllegalArgumentException("Linear movement requires one step without a delay");
			}
			steps = new ArrayList<>(value);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 777;
		}
	}

	static final class Protocol777 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
					ByteBufStreamCodecs.VARINT.encode(output, (packet.onGround ? 1 : 0)
						| (packet.stepped ? packet.steps.size() << 1 : 0));
					if (!packet.stepped && packet.steps.size() != 1)
						throw new IllegalArgumentException("Linear movement requires one step");
					for (EntityRelativeMoveStep step : packet.steps) {
						if (packet.stepped) ByteBufStreamCodecs.VARINT.encode(output, step.delayTicks());
						RawVector3d.SHORT_DELTA_STREAM_CODEC.encode(output, step.delta());
					}
				},
				input -> {
					int entityId = ByteBufStreamCodecs.VARINT.decode(input);
					int count;

					int properties = ByteBufStreamCodecs.VARINT.decode(input);
					boolean onGround = (properties & 1) != 0;
					int stepCount = properties >>> 1;
					boolean stepped = stepCount != 0;
					count = stepped ? stepCount : 1;

					if (count > input.readableBytes() / 6)
						throw new IllegalArgumentException("Too many movement steps");
					List<EntityRelativeMoveStep> steps = new ArrayList<>(count);
					for (int i = 0; i < count; i++) {
						final int ticks = stepped ? ByteBufStreamCodecs.VARINT.decode(input) : 0;
						final RawVector3d delta = RawVector3d.SHORT_DELTA_STREAM_CODEC.decode(input);
						steps.add(new Step(delta, ticks));
					}
					return new Protocol777(entityId, onGround, stepped, steps);
				});

		public Protocol777() {
			super();
		}

		public Protocol777(
			int entityId, boolean onGround, boolean stepped, List<EntityRelativeMoveStep> steps) {
			super(entityId, onGround, stepped, steps);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 777;
		}

		@Override
		public void setSteps(List<? extends EntityRelativeMoveStep> value) {
			markMutated();
			boolean requiresSteps = value.size() != 1 || value.get(0).delayTicks() != 0;

			if (value.isEmpty()) throw new IllegalArgumentException("Empty movement");
			steps = new ArrayList<>(value);
			stepped = requiresSteps;
		}
	}

	public static final class Step implements EntityRelativeMoveStep {
		private final RawVector3d delta;
		private final int ticks;

		public Step(RawVector3d delta, int ticks) {
			this.delta = delta;
			this.ticks = ticks;
		}

		@Override
		public RawVector3d delta() {
			return delta;
		}

		@Override
		public int delayTicks() {
			return ticks;
		}
	}
}

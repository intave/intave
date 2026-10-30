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

package de.jpx3.intave.packet.wire.entity.relativemove;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableEntityRelativeMovePacket;
import de.jpx3.intave.share.EntityRelativeMoveStep;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EntityRelativeMoveWirePacket implements MutableEntityRelativeMovePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityRelativeMoveWirePacket> STREAM_CODEC =
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
						int count = 1;

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
						return new EntityRelativeMoveWirePacket(entityId, onGround, stepped, steps);
					});

	protected int entityId = 0;

	protected boolean onGround, stepped;

	protected List<EntityRelativeMoveStep> steps =
			new ArrayList<>(Collections.singletonList(new Step(new RawVector3d(0, 0, 0), 0)));

	public EntityRelativeMoveWirePacket() {
		this(
				0,
				false,
				false,
				new ArrayList<>(Collections.singletonList(new Step(new RawVector3d(0, 0, 0), 0))));
	}

	public EntityRelativeMoveWirePacket(
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
		this.entityId = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("REL_ENTITY_MOVE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 777;
	}

	@Override
	public List<EntityRelativeMoveStep> steps() {
		return Collections.unmodifiableList(steps);
	}

	@Override
	public void setSteps(List<? extends EntityRelativeMoveStep> value) {
		boolean requiresSteps = value.size() != 1 || value.get(0).delayTicks() != 0;

		if (value.isEmpty()) throw new IllegalArgumentException("Empty movement");
		steps = new ArrayList<>(value);
		stepped = requiresSteps;
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

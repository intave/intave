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

package de.jpx3.intave.packet.wire.entity.relativemovelook;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.EntityRelativeMoveStep;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

public final class EntityRelativeMoveLookShortDeltasWirePacket extends EntityRelativeMoveLookWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityRelativeMoveLookShortDeltasWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
								EntityRelativeMoveStep step = packet.steps.get(0);
								RawVector3d.SHORT_DELTA_STREAM_CODEC.encode(output, step.delta());
								output.writeByte(packet.yaw);
								output.writeByte(packet.pitch);
								output.writeBoolean(packet.onGround);
							},
							input -> {
								int entityId = ByteBufStreamCodecs.VARINT.decode(input);
								RawVector3d delta = RawVector3d.SHORT_DELTA_STREAM_CODEC.decode(input);
								List<EntityRelativeMoveStep> steps = new ArrayList<>();
								steps.add(new Step(delta, 0));
								byte yaw = input.readByte();
								byte pitch = input.readByte();
								boolean onGround = input.readBoolean();
								return new EntityRelativeMoveLookShortDeltasWirePacket(
										entityId, steps, yaw, pitch, onGround);
							});

	public EntityRelativeMoveLookShortDeltasWirePacket() {
		super();
	}

	public EntityRelativeMoveLookShortDeltasWirePacket(
			int entityId,
			List<EntityRelativeMoveStep> steps,
			byte yaw,
			byte pitch,
			boolean onGround) {
		super(entityId, onGround, false, steps, yaw, pitch);
	}

	@Override
	public void setSteps(List<? extends EntityRelativeMoveStep> value) {
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

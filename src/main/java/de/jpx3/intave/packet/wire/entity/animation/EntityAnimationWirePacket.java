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

package de.jpx3.intave.packet.wire.entity.animation;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableEntityAnimationPacket;
import de.jpx3.intave.share.EntityAnimation;
import io.netty.buffer.ByteBuf;

public class EntityAnimationWirePacket implements MutableEntityAnimationPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityAnimationWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityAnimationWirePacket::entityId,
			ByteBufStreamCodecs.enumCodec(EntityAnimation.values(), ByteBufStreamCodecs.UNSIGNED_BYTE), EntityAnimationWirePacket::animation,
			EntityAnimationWirePacket::new
		);

	protected int entityId;
	protected EntityAnimation animation;

	public EntityAnimationWirePacket() {
		this(0, EntityAnimation.SWING);
	}

	public EntityAnimationWirePacket(int entityId, EntityAnimation animation) {
		this.entityId = entityId;
		this.animation = animation;
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
	public EntityAnimation animation() {
		return this.animation;
	}

	@Override
	public void setAnimation(EntityAnimation value) {
		this.animation = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ANIMATION".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}

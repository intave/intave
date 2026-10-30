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

package de.jpx3.intave.packet.wire.entity.use;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableEntityUsePacket;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public class EntityUseWithoutHandWirePacket implements MutableEntityUsePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityUseWithoutHandWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityUseWithoutHandWirePacket::entityId,
			EntityUseInteraction.WITHOUT_HAND_STREAM_CODEC, EntityUseWithoutHandWirePacket::interaction,
			EntityUseWithoutHandWirePacket::new
		);

	private int entityId;
	private EntityUseAction action;
	private RawVector3d hitPosition;

	public EntityUseWithoutHandWirePacket() {
		this(0, EntityUseAction.INTERACT, null);
	}

	public EntityUseWithoutHandWirePacket(int entityId, EntityUseInteraction interaction) {
		this(entityId, interaction.action(), interaction.hitPosition());
	}

	public EntityUseWithoutHandWirePacket(int entityId, EntityUseAction action, RawVector3d hitPosition) {
		this.entityId = entityId;
		this.action = Objects.requireNonNull(action, "action");
		this.hitPosition = hitPosition;
	}

	public EntityUseInteraction interaction() {
		return new EntityUseInteraction(action(), hitPosition(), hand());
	}

	@Override
	public int entityId() {
		return entityId;
	}

	@Override
	public void setEntityId(int value) {
		entityId = value;
	}

	@Override
	public EntityUseAction action() {
		return action;
	}

	@Override
	public void setAction(EntityUseAction value) {
		action = Objects.requireNonNull(value, "action");
	}

	@Override
	public RawVector3d hitPosition() {
		return action == EntityUseAction.INTERACT_AT ? hitPosition : null;
	}

	@Override
	public void setHitPosition(RawVector3d value) {
		hitPosition = value;
	}

	@Override
	public PlayerHand hand() {
		return null;
	}

	@Override
	public void setHand(PlayerHand value) {
		if (value != null) throw new UnsupportedOperationException("This packet does not support a hand");
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "USE_ENTITY".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}
}

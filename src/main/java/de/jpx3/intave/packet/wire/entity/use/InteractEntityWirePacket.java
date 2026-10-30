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

public class InteractEntityWirePacket implements MutableEntityUsePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, InteractEntityWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, InteractEntityWirePacket::entityId,
			ByteBufStreamCodecs.enumCodec(PlayerHand.values()), InteractEntityWirePacket::hand,
			RawVector3d.LOW_PRECISION_STREAM_CODEC, InteractEntityWirePacket::hitPosition,
			ByteBufStreamCodecs.BOOLEAN, InteractEntityWirePacket::secondary,
			InteractEntityWirePacket::new
		);

	protected int entityId = 0;

	protected PlayerHand hand = PlayerHand.MAIN_HAND;

	protected RawVector3d hitPosition = new RawVector3d(0, 0, 0);

	private boolean secondary;

	public InteractEntityWirePacket() {
		this(0, PlayerHand.MAIN_HAND, new RawVector3d(0, 0, 0), false);
	}

	public InteractEntityWirePacket(
			int entityId, PlayerHand hand, RawVector3d hitPosition, boolean secondary) {
		this.entityId = entityId;
		this.hand = hand;
		this.hitPosition = hitPosition;
		this.secondary = secondary;
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
	public PlayerHand hand() {
		return this.hand;
	}

	@Override
	public void setHand(PlayerHand value) {
		this.hand = value;
	}

	@Override
	public RawVector3d hitPosition() {
		return this.hitPosition;
	}

	@Override
	public void setHitPosition(RawVector3d value) {
		this.hitPosition = value;
	}

	public boolean secondary() {
		return secondary;
	}

	public void setSecondary(boolean value) {
		secondary = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("USE_ENTITY".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 775;
	}

	@Override
	public EntityUseAction action() {
		return EntityUseAction.INTERACT_AT;
	}

	@Override
	public void setAction(EntityUseAction value) {
		if (value != EntityUseAction.INTERACT_AT)
			throw new IllegalArgumentException("Use the dedicated attack packet for attacks");
	}
}

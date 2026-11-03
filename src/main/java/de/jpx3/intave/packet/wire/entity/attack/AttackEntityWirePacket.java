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

package de.jpx3.intave.packet.wire.entity.attack;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutableEntityUsePacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

public class AttackEntityWirePacket extends MutableWirePacket implements MutableEntityUsePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, AttackEntityWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, AttackEntityWirePacket::entityId,
			AttackEntityWirePacket::new
		);

	protected int entityId;

	public AttackEntityWirePacket() {
		this(0);
	}

	public AttackEntityWirePacket(int entityId) {
		this.entityId = entityId;
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
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 775;
	}

	@Override
	public EntityUseAction action() {
		return EntityUseAction.ATTACK;
	}

	@Override
	public void setAction(EntityUseAction value) {
		markMutated();
		if (value != EntityUseAction.ATTACK) {
			throw new IllegalArgumentException("Expected ATTACK");
		}
	}

	@Override
	public RawVector3d hitPosition() {
		return null;
	}

	@Override
	public void setHitPosition(RawVector3d value) {
		markMutated();
		if (value != null) {
			throw new IllegalArgumentException("Attack has no hit position");
		}
	}

	@Override
	public PlayerHand hand() {
		return null;
	}

	@Override
	public void setHand(PlayerHand value) {
		markMutated();
		if (value != null) {
			throw new IllegalArgumentException("Attack has no hand");
		}
	}
}

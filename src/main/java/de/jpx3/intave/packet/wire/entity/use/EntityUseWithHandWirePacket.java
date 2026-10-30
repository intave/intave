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
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

public class EntityUseWithHandWirePacket extends EntityUseWithoutHandWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityUseWithHandWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityUseWithoutHandWirePacket::entityId,
			EntityUseInteraction.STREAM_CODEC, EntityUseWithoutHandWirePacket::interaction,
			EntityUseWithHandWirePacket::new
		);

	private PlayerHand hand;

	public EntityUseWithHandWirePacket() {
		this(0, EntityUseAction.INTERACT, null, PlayerHand.MAIN_HAND);
	}

	public EntityUseWithHandWirePacket(int entityId, EntityUseInteraction interaction) {
		this(entityId, interaction.action(), interaction.hitPosition(), interaction.hand());
	}

	public EntityUseWithHandWirePacket(
		int entityId, EntityUseAction action, RawVector3d hitPosition, PlayerHand hand
	) {
		super(entityId, action, hitPosition);
		this.hand = hand;
	}

	@Override
	public PlayerHand hand() {
		return action() == EntityUseAction.ATTACK ? null : hand;
	}

	@Override
	public void setHand(PlayerHand value) {
		hand = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107 && protocolVersion < 735;
	}
}

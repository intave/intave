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

public final class EntityUseWirePacket extends EntityUseWithHandWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityUseWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, EntityUseWithoutHandWirePacket::entityId,
			EntityUseInteraction.STREAM_CODEC, EntityUseWithoutHandWirePacket::interaction,
			ByteBufStreamCodecs.BOOLEAN, EntityUseWirePacket::secondary,
			EntityUseWirePacket::new
		);

	private boolean secondary;

	public EntityUseWirePacket() {
		super();
	}

	public EntityUseWirePacket(int entityId, EntityUseInteraction interaction, boolean secondary) {
		super(entityId, interaction);
		this.secondary = secondary;
	}

	public EntityUseWirePacket(
		int entityId, EntityUseAction action, RawVector3d hitPosition, PlayerHand hand, boolean secondary
	) {
		super(entityId, action, hitPosition, hand);
		this.secondary = secondary;
	}

	public boolean secondary() {
		return secondary;
	}

	public void setSecondary(boolean value) {
		secondary = value;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 735 && protocolVersion < 775;
	}
}

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

package de.jpx3.intave.packet.contract.immutable;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.RawVector3d;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.contract.PacketType.Client.ATTACK_ENTITY;
import static de.jpx3.intave.packet.contract.PacketType.Client.USE_ENTITY;

public interface EntityUsePacket extends PacketContract, SingleEntityAddressed, Handed {
	EntityUseAction action();

	@Nullable
	RawVector3d hitPosition();

	default boolean hasHitPosition() {
		return hitPosition() != null;
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == PLAY
			&& (packetType == ATTACK_ENTITY || packetType == USE_ENTITY);
	}
}

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
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.PlayerDigAction;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.contract.PacketType.Client.BLOCK_DIG;

public interface BlockDigPacket extends PacketContract, BlockAddressed {
	@Nullable
	PlayerDigAction action();

	@Nullable
	Direction direction();

	default boolean hasAction() {
		return action() != null;
	}

	default boolean hasDirection() {
		return direction() != null;
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == PLAY && packetType == BLOCK_DIG;
	}
}

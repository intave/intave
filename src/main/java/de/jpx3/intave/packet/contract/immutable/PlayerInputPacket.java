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
import de.jpx3.intave.share.Input;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.contract.PacketType.Client.PLAYER_INPUT;

public interface PlayerInputPacket extends PacketContract {
	@Nullable
	Input input();

	@Nullable
	default Float forward() {
		return Float.valueOf(input() != null ? input().forward() : null);
	}

	@Nullable
	default Float strafe() {
		return Float.valueOf(input() != null ? input().strafe() : null);
	}

	default boolean jumping() {
		return input() != null && input().jumpKey();
	}

	default boolean hasDigitalInput() {
		return input() != null;
	}

	default boolean hasAnalogInput() {
		return forward() != null && strafe() != null;
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == PLAY && packetType == PLAYER_INPUT;
	}
}

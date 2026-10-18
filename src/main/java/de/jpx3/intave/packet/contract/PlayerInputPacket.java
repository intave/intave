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

package de.jpx3.intave.packet.contract;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.share.Input;

public interface PlayerInputPacket extends Packet {
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
	default boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && name.equals("PLAYER_INPUT");
	}
}

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

import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Relative;

import java.util.OptionalInt;
import java.util.Set;

public interface PlayerTeleportPacket extends Packet, Positioned, Rotated, MotionUpdate {
	OptionalInt teleportId();

	Set<Relative> flags();

	default boolean hasTeleportId() {
		return teleportId().isPresent();
	}

	@Override
	default boolean hasPosition() {
		return true;
	}

	@Override
	default boolean hasRotation() {
		return true;
	}

	default PositionMoveRotation toPosMoveRot() {
		return new PositionMoveRotation(position(), motion(), rotation());
	}
}

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

package de.jpx3.intave.packet.contract.mutable;

import de.jpx3.intave.packet.contract.PlayerTeleportPacket;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Relative;

import java.util.Set;

public interface MutablePlayerTeleportPacket extends PlayerTeleportPacket, MutablePositioned, MutableRotated, MutableMotionUpdate {

	void setTeleportId(int teleportId);

	void setFlags(Set<Relative> flags);

	default void setPosMoveRot(PositionMoveRotation posRotMove) {
		setPosition(posRotMove.position());
		setRotation(posRotMove.rotation());
		setMotion(posRotMove.motion());
	}
}

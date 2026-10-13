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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.contract.BlockInteractionPacket;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.RawVector3d;

public interface MutableBlockInteractionPacket extends BlockInteractionPacket, MutableBlockAddressed, MutableHanded, MutableSequenced {

	void setDirection(@Nullable Direction direction);

	void setHitPosition(@Nullable RawVector3d hitPosition);

	default void clearDirection() {
		setDirection(null);
	}

	default void clearHitPosition() {
		setHitPosition(null);
	}
}

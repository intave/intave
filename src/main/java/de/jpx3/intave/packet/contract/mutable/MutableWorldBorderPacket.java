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
import de.jpx3.intave.packet.contract.immutable.WorldBorderPacket;
import de.jpx3.intave.share.Position;

public interface MutableWorldBorderPacket extends WorldBorderPacket, Mutable {
	void setCenter(Position center);

	@Nullable
	default Position mutableCenter() {
		Position center = center();
		if (center != null && center.isImmutable()) {
			center = center.mutable();
			setCenter(center);
		}
		return center;
	}

	void setSize(double size);

	void setLerp(double oldSize, double newSize, long milliseconds);

	void setAbsoluteMaxSize(int size);
}

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
import de.jpx3.intave.share.Position;

public interface WorldBorderPacket extends Packet {

	@Nullable
	Position center();

	@Nullable
	Double size();

	@Nullable
	Double oldSize();

	@Nullable
	Double newSize();

	@Nullable
	Long lerpTime();

	@Nullable
	Integer absoluteMaxSize();

	default boolean updatesCenter() {
		return center() != null;
	}

	default boolean updatesSize() {
		return size() != null;
	}

	default boolean updatesLerp() {
		return oldSize() != null && newSize() != null && lerpTime() != null;
	}

	default boolean updatesAbsoluteMaxSize() {
		return absoluteMaxSize() != null;
	}
}

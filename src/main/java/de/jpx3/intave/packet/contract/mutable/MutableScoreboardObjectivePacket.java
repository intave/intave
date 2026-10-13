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
import de.jpx3.intave.packet.contract.ScoreboardObjectivePacket;
import de.jpx3.intave.share.ScoreboardObjectiveAction;

public interface MutableScoreboardObjectivePacket extends ScoreboardObjectivePacket, MutableObjectiveAddressed {

	void setAction(ScoreboardObjectiveAction action);

	void setDisplayNameJson(@Nullable String json);

	default void clearDisplayNameJson() {
		setDisplayNameJson(null);
	}
}

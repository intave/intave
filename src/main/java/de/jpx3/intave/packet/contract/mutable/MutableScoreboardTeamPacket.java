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
import de.jpx3.intave.packet.contract.ScoreboardTeamPacket;
import de.jpx3.intave.share.ScoreboardTeamAction;

import java.util.List;

public interface MutableScoreboardTeamPacket extends ScoreboardTeamPacket {

	void setName(String name);

	void setAction(ScoreboardTeamAction action);

	void setPrefixJson(@Nullable String json);

	void setSuffixJson(@Nullable String json);

	void setColor(@Nullable String color);

	void setEntries(List<String> entries);

	default void clearPrefixJson() {
		setPrefixJson(null);
	}

	default void clearSuffixJson() {
		setSuffixJson(null);
	}

	default void clearColor() {
		setColor(null);
	}
}

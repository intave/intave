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
import de.jpx3.intave.share.ChatComponent;
import de.jpx3.intave.share.ScoreboardTeamAction;

import java.util.List;

public interface ScoreboardTeamPacket extends PacketContract {
	String name();

	ScoreboardTeamAction action();

	@Nullable
	String prefixJson();

	@Nullable
	String suffixJson();

	@Nullable
	String color();

	List<String> entries();

	default String prefixText() {
		String json = prefixJson();
		return json == null ? null : ChatComponent.json(json).legacyText();
	}

	default String suffixText() {
		String json = suffixJson();
		return json == null ? null : ChatComponent.json(json).legacyText();
	}

	@Override
	default boolean appliesTo(ConnectionState state, PacketType packetType) {
		return state == ConnectionState.PLAY && packetType == PacketType.Server.SCOREBOARD_TEAM;
	}

	default boolean hasPrefixJson() {
		return prefixJson() != null;
	}

	default boolean hasSuffixJson() {
		return suffixJson() != null;
	}

	default boolean hasColor() {
		return color() != null;
	}
}

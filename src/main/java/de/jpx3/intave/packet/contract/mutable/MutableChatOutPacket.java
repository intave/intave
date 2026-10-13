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

import de.jpx3.intave.packet.contract.ChatOutPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface MutableChatOutPacket extends ChatOutPacket {

	void setLinesJson(List<String> lines);

	void setChannel(Channel channel);

	default void setLineJson(int index, String line) {
		List<String> lines = new ArrayList<>(linesJson());
		lines.set(index, line);
		setLinesJson(lines);
	}

	default void setLineJson(String line) {
		setLinesJson(Collections.singletonList(line));
	}

	default void addLineJson(String line) {
		List<String> lines = new ArrayList<>(linesJson());
		lines.add(line);
		setLinesJson(lines);
	}
}

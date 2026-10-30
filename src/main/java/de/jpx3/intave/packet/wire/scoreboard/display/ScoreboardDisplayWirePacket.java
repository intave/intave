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

package de.jpx3.intave.packet.wire.scoreboard.display;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardDisplayPacket;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;

public class ScoreboardDisplayWirePacket implements MutableScoreboardDisplayPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ScoreboardDisplayWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.slot);
						ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objectiveName == null ? "" : packet.objectiveName);
					},
					input -> {
						int slot = ByteBufStreamCodecs.VARINT.decode(input);
						String name = ByteBufStreamCodecs.VARINT_STRING.decode(input);
						String objectiveName = name.isEmpty() ? null : name;
						return new ScoreboardDisplayWirePacket(slot, objectiveName);
					});

	protected static final String[] COLORS = {
		"black",
		"dark_blue",
		"dark_green",
		"dark_aqua",
		"dark_red",
		"dark_purple",
		"gold",
		"gray",
		"dark_gray",
		"blue",
		"green",
		"aqua",
		"red",
		"light_purple",
		"yellow",
		"white"
	};

	protected String objectiveName = null;

	protected int slot;

	public ScoreboardDisplayWirePacket() {
		this(0, null);
	}

	public ScoreboardDisplayWirePacket(int slot, String objectiveName) {
		this.slot = slot;
		this.objectiveName = objectiveName;
	}

	@Override
	public String objectiveName() {
		return this.objectiveName;
	}

	@Override
	public void setObjectiveName(String value) {
		this.objectiveName = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("SCOREBOARD_DISPLAY_OBJECTIVE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 764;
	}

	@Override
	public String displaySlot() {
		if (slot == 0) return "list";
		if (slot == 1) return "sidebar";
		if (slot == 2) return "below_name";
		return slot >= 3 && slot < 19 ? "team_" + COLORS[slot - 3] : "other_" + slot;
	}

	@Override
	public void setDisplaySlot(String value) {
		if (value.equals("list")) slot = 0;
		else if (value.equals("sidebar")) slot = 1;
		else if (value.equals("below_name")) slot = 2;
		else if (value.startsWith("team_") && Arrays.asList(COLORS).contains(value.substring(5)))
			slot = 3 + Arrays.asList(COLORS).indexOf(value.substring(5));
		else if (value.startsWith("other_")) slot = Integer.parseInt(value.substring(6));
		else throw new IllegalArgumentException("Unknown display slot " + value);
	}
}

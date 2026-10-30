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

package de.jpx3.intave.packet.wire.scoreboard.objective;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardObjectivePacket;
import de.jpx3.intave.share.ScoreboardObjectiveAction;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public class ScoreboardObjectiveWirePacket implements MutableScoreboardObjectivePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ScoreboardObjectiveWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT_STRING.encode(output, packet.objectiveName);
						output.writeByte(packet.action.id());
						if (packet.action != ScoreboardObjectiveAction.REMOVE) {
							ByteBufStreamCodecs.stringCodec(262144).encode(output, packet.display);
							ByteBufStreamCodecs.VARINT.encode(output, packet.renderType);
						}
					},
					input -> {
						String display = "\"\"";
						int renderType = 0;

						String objectiveName = ByteBufStreamCodecs.VARINT_STRING.decode(input);
						ScoreboardObjectiveAction action =
								ScoreboardObjectiveAction.byId(input.readUnsignedByte());
						if (action != ScoreboardObjectiveAction.REMOVE) {
							display = ByteBufStreamCodecs.stringCodec(262144).decode(input);
							renderType = ByteBufStreamCodecs.VARINT.decode(input);
						}
						return new ScoreboardObjectiveWirePacket(
								objectiveName, action, display, renderType);
					});

	protected String objectiveName = "";

	protected ScoreboardObjectiveAction action = ScoreboardObjectiveAction.CREATE;

	protected String display = "\"\"";

	protected int renderType;

	public ScoreboardObjectiveWirePacket() {
		this("", ScoreboardObjectiveAction.CREATE, "\"\"", 0);
	}

	public ScoreboardObjectiveWirePacket(
			String objectiveName,
			ScoreboardObjectiveAction action,
			String display,
			int renderType) {
		this.objectiveName = objectiveName;
		this.action = action;
		this.display = display;
		this.renderType = renderType;
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
	public ScoreboardObjectiveAction action() {
		return this.action;
	}

	@Override
	public void setAction(ScoreboardObjectiveAction value) {
		this.action = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("SCOREBOARD_OBJECTIVE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 393 && protocolVersion < 765;
	}

	@Override
	public String displayNameJson() {
		return action == ScoreboardObjectiveAction.REMOVE ? null : display;
	}

	@Override
	public void setDisplayNameJson(String value) {
		display = Objects.requireNonNull(value);
	}
}

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

package de.jpx3.intave.packet.wire.scoreboard.score;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.mutable.MutableScoreboardScorePacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class ResetScoreWirePacket extends AbstractMutable implements MutableScoreboardScorePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, ResetScoreWirePacket> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.VARINT_STRING, ResetScoreWirePacket::owner,
		ByteBufStreamCodecs.optionalCodecOf(ByteBufStreamCodecs.VARINT_STRING), ResetScoreWirePacket::objectiveName,
		ResetScoreWirePacket::new
	);

	private String owner;
	private String objective;

	public ResetScoreWirePacket() {
		this("", null);
	}

	public ResetScoreWirePacket(String owner, String objective) {
		this.owner = Objects.requireNonNull(owner);
		this.objective = objective;
	}

	@Override
	public String owner() {
		return owner;
	}

	@Override
	public String objectiveName() {
		return objective;
	}

	@Override
	public boolean removed() {
		return true;
	}

	@Override
	public Integer value() {
		return null;
	}

	@Override
	public String displayNameJson() {
		return null;
	}

	@Override
	public void setOwner(String value) {
		markMutated();
		owner = Objects.requireNonNull(value);
	}

	@Override
	public void setObjectiveName(String value) {
		markMutated();
		objective = value;
	}

	@Override
	public void setRemoved(boolean value) {
		if (!value) throw new UnsupportedOperationException("Use SCOREBOARD_SCORE to set a score");
	}

	@Override
	public void setValue(int value) {
		throw new UnsupportedOperationException("Reset scores have no value");
	}

	@Override
	public void setDisplayNameJson(String value) {
		if (value != null) throw new UnsupportedOperationException("Reset scores have no display name");
	}

	@Override
	public boolean appliesTo(ConnectionState state, PacketType type) {
		return state == ConnectionState.PLAY && type == PacketType.Server.RESET_SCORE;
	}

	@Override
	public boolean appliesTo(int protocol) {
		return protocol >= 765;
	}
}

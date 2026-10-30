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

package de.jpx3.intave.packet.wire.world.gamestate;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableGameStateChangePacket;
import de.jpx3.intave.share.GameStateChangeType;
import io.netty.buffer.ByteBuf;

public class GameStateChangeWirePacket implements MutableGameStateChangePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, GameStateChangeWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, GameStateChangeWirePacket::typeId,
			ByteBufStreamCodecs.FLOAT, GameStateChangeWirePacket::value,
			GameStateChangeWirePacket::new
		);

	protected float value = 0;

	private int typeId;

	public GameStateChangeWirePacket() {
		this(0, 0);
	}

	public GameStateChangeWirePacket(int typeId, float value) {
		this.typeId = typeId;
		this.value = value;
	}

	@Override
	public float value() {
		return this.value;
	}

	@Override
	public void setValue(float value) {
		this.value = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("GAME_STATE_CHANGE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}

	@Override
	public GameStateChangeType type() {
		return GameStateChangeType.byId(typeId);
	}

	@Override
	public void setType(GameStateChangeType type) {
		typeId = type.id();
	}

	public int typeId() {
		return typeId;
	}
}

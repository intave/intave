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

package de.jpx3.intave.packet.wire.player.health;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableUpdateHealthPacket;
import io.netty.buffer.ByteBuf;

public class UpdateHealthWirePacket implements MutableUpdateHealthPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, UpdateHealthWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.FLOAT, UpdateHealthWirePacket::health,
			ByteBufStreamCodecs.VARINT, UpdateHealthWirePacket::foodLevel,
			ByteBufStreamCodecs.FLOAT, UpdateHealthWirePacket::saturation,
			UpdateHealthWirePacket::new
		);

	protected float health = 20;

	protected int foodLevel = 20;

	protected float saturation = 5;

	public UpdateHealthWirePacket() {
		this(20, 20, 5);
	}

	public UpdateHealthWirePacket(float health, int foodLevel, float saturation) {
		this.health = health;
		this.foodLevel = foodLevel;
		this.saturation = saturation;
	}

	@Override
	public float health() {
		return this.health;
	}

	@Override
	public void setHealth(float value) {
		this.health = value;
	}

	@Override
	public int foodLevel() {
		return this.foodLevel;
	}

	@Override
	public void setFoodLevel(int value) {
		this.foodLevel = value;
	}

	@Override
	public float saturation() {
		return this.saturation;
	}

	@Override
	public void setSaturation(float value) {
		this.saturation = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("UPDATE_HEALTH".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}

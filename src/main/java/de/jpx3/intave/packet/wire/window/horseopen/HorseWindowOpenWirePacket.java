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

package de.jpx3.intave.packet.wire.window.horseopen;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableWindowOpenPacket;
import io.netty.buffer.ByteBuf;

import java.util.OptionalInt;

public class HorseWindowOpenWirePacket implements MutableWindowOpenPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, HorseWindowOpenWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.VARINT, HorseWindowOpenWirePacket::containerId,
			ByteBufStreamCodecs.VARINT, HorseWindowOpenWirePacket::count,
			ByteBufStreamCodecs.INTEGER, HorseWindowOpenWirePacket::entityId,
			HorseWindowOpenWirePacket::new
		);

	protected int containerId = 0;

	protected int count, entityId;

	public HorseWindowOpenWirePacket() {
		this(0, 0, 0);
	}

	public HorseWindowOpenWirePacket(int containerId, int count, int entityId) {
		this.containerId = containerId;
		this.count = count;
		this.entityId = entityId;
	}

	@Override
	public int containerId() {
		return this.containerId;
	}

	@Override
	public void setContainerId(int value) {
		this.containerId = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("OPEN_WINDOW_HORSE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 770;
	}

	@Override
	public String menuType() {
		return "minecraft:horse";
	}

	@Override
	public void setMenuType(String value) {
		if (!"minecraft:horse".equals(value))
			throw new IllegalArgumentException("Expected horse menu");
	}

	@Override
	public int slots() {
		return count * 3 + (0);
	}

	@Override
	public void setSlots(int value) {
		int offset = 0;
		if (((value < offset || (value - offset) % 3 != 0)))
			throw new IllegalArgumentException("Horse slots must contain complete columns");
		count = (value - offset) / 3;
	}

	@Override
	public OptionalInt optionalEntityId() {
		return OptionalInt.of(entityId);
	}

	@Override
	public void setEntityId(int value) {
		entityId = value;
	}

	public int count() {
		return count;
	}

	public int entityId() {
		return entityId;
	}
}

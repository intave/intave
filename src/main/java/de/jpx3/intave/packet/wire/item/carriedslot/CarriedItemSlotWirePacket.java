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

package de.jpx3.intave.packet.wire.item.carriedslot;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableHeldItemSlotPacket;
import io.netty.buffer.ByteBuf;

public class CarriedItemSlotWirePacket implements MutableHeldItemSlotPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, CarriedItemSlotWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.SIGNED_BYTE, CarriedItemSlotWirePacket::slot,
			CarriedItemSlotWirePacket::new
		);

	protected int slot = 0;

	public CarriedItemSlotWirePacket() {
		this(0);
	}

	public CarriedItemSlotWirePacket(int slot) {
		this.slot = slot;
	}

	@Override
	public int slot() {
		return this.slot;
	}

	@Override
	public void setSlot(int value) {
		this.slot = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("HELD_ITEM_SLOT".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47;
	}
}

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

package de.jpx3.intave.packet.wire.window.transaction;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableTransactionPacket;
import io.netty.buffer.ByteBuf;

public class TransactionWirePacket implements MutableTransactionPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, TransactionWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			ByteBufStreamCodecs.UNSIGNED_BYTE, TransactionWirePacket::containerId,
			ByteBufStreamCodecs.SHORT, TransactionWirePacket::actionNumber,
			ByteBufStreamCodecs.BOOLEAN, TransactionWirePacket::accepted,
			TransactionWirePacket::new
		);

	protected int containerId;
	protected short actionNumber;
	protected boolean accepted;

	public TransactionWirePacket() {
		this(0, (short) 0, false);
	}

	public TransactionWirePacket(int containerId, short actionNumber, boolean accepted) {
		this.containerId = containerId;
		this.actionNumber = actionNumber;
		this.accepted = accepted;
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
	public short actionNumber() {
		return this.actionNumber;
	}

	@Override
	public void setActionNumber(short value) {
		this.actionNumber = value;
	}

	@Override
	public boolean accepted() {
		return this.accepted;
	}

	@Override
	public void setAccepted(boolean value) {
		this.accepted = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("TRANSACTION".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 755;
	}
}

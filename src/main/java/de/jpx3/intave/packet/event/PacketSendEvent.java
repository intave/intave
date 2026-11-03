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

package de.jpx3.intave.packet.event;

import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;

public final class PacketSendEvent extends PacketEvent {
	private final PacketType.Server type;
	private List<TypedPacketContract> sendBefore;
	private List<TypedPacketContract> sendAfter;

	public PacketSendEvent(
		User user, TypedPacketContract tpc
	) {
		super(user, tpc.contract());
		this.type = (PacketType.Server) tpc.type();
	}

	public PacketSendEvent(
		User user, PacketType.Server type, PacketContract contract
	) {
		super(user, contract);
		this.type = type;
	}

	public PacketType.Server packetType() {
		return type;
	}

	public TypedPacketContract typedPacket() {
		return super.packet().typedTo(type);
	}

	public List<TypedPacketContract> sendBefore() {
		return sendBefore == null ? Collections.emptyList() : sendBefore;
	}

	public void addSendBefore(TypedPacketContract packet) {
		if (sendBefore == null) {
			sendBefore = new ArrayList<>(1);
		}
		sendBefore.add(packet);
	}

	public List<TypedPacketContract> sendAfter() {
		return sendAfter == null ? Collections.emptyList() : sendAfter;
	}

	public void addSendAfter(TypedPacketContract packet) {
		if (sendAfter == null) {
			sendAfter = new ArrayList<>(1);
		}
		sendAfter.add(packet);
	}

	public boolean otherPacketsToSend() {
		return (sendBefore != null && !sendBefore.isEmpty()) || (sendAfter != null && !sendAfter.isEmpty());
	}

	@Override
	public PacketDirection direction() {
		return CLIENTBOUND;
	}
}

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
import de.jpx3.intave.user.User;

public final class PacketReceiveEvent extends PacketEvent {
  private final PacketType.Client packetId;

	public PacketReceiveEvent(
		User user, PacketType.Client packetId, PacketContract packet
	) {
		super(user, packet);
		this.packetId = packetId;
	}

	@Override
	public PacketDirection direction() {
		return PacketDirection.SERVERBOUND;
	}

	public PacketType.Client packetId() {
		return packetId;
	}
}

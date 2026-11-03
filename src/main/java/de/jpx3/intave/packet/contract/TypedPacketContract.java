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

package de.jpx3.intave.packet.contract;

import java.util.Objects;

public final class TypedPacketContract {
	private final PacketType packetType;
	private final PacketContract contract;

	public TypedPacketContract(
		PacketType packetType, PacketContract contract
	) {
		this.packetType = Objects.requireNonNull(packetType, "packet type");
		this.contract = Objects.requireNonNull(contract, "packet contract");
	}

	public PacketType type() {
		return packetType;
	}

	public PacketContract contract() {
		return contract;
	}

	public static TypedPacketContract of(PacketType packetType, PacketContract contract) {
		return new TypedPacketContract(packetType, contract);
	}

	@Override
	public int hashCode() {
		int result = packetType.hashCode();
		result = 31 * result + contract.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		TypedPacketContract that = (TypedPacketContract) obj;
		if (!Objects.equals(packetType, that.packetType)) return false;
		return Objects.equals(contract, that.contract);
	}

	@Override
	public String toString() {
		return "IdentifiedPacketContract{" +
			"packetId=" + packetType +
			", contract=" + contract +
			'}';
	}
}

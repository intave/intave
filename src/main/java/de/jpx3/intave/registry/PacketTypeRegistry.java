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

package de.jpx3.intave.registry;

import com.google.common.collect.ImmutableBiMap;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

public final class PacketTypeRegistry<T extends Enum<T> & PacketType> {
	private final Set<Integer> supportedProtocolVersions;
	private final IntFunction<Map<ConnectionState, ImmutableBiMap<T, Integer>>> statesForProtocol;

	PacketTypeRegistry(Set<Integer> supportedProtocolVersions, IntFunction<Map<ConnectionState, ImmutableBiMap<T, Integer>>> statesForProtocol) {
		this.supportedProtocolVersions = supportedProtocolVersions;
		this.statesForProtocol = statesForProtocol;
	}

	public int idOf(ConnectionState connectionState, PacketType packetType, int protocolVersion) {
		Objects.requireNonNull(packetType, "packetType");
		return table(connectionState, protocolVersion).getOrDefault(packetType, -1);
	}

	public @Nullable T typeOf(ConnectionState connectionState, int packetId, int protocolVersion) {
		return table(connectionState, protocolVersion).inverse().get(packetId);
	}

	public Map<T, Integer> packetIds(ConnectionState connectionState, int protocolVersion) {
		return table(connectionState, protocolVersion);
	}

	public Set<Integer> supportedProtocolVersions() {
		return supportedProtocolVersions;
	}

	private ImmutableBiMap<T, Integer> table(ConnectionState state, int protocolVersion) {
		Objects.requireNonNull(state, "connectionState");
		return statesForProtocol.apply(protocolVersion).getOrDefault(state, ImmutableBiMap.of());
	}
}

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

import de.jpx3.intave.packet.ConnectionState;

import java.util.*;

public final class PacketIdRegistry {
	private final Map<Integer, Map<ConnectionState, Map<String, Integer>>> protocols;
	private final Map<Integer, Map<ConnectionState, String[]>> typeNames;
	private final Set<Integer> supportedProtocolVersions;

	PacketIdRegistry(Map<Integer, Map<ConnectionState, Map<String, Integer>>> protocols) {
		Map<Integer, Map<ConnectionState, Map<String, Integer>>> copy = new HashMap<>();
		Map<Integer, Map<ConnectionState, String[]>> typeNames = new HashMap<>();
		protocols.forEach((protocol, states) -> {
			Map<ConnectionState, Map<String, Integer>> stateCopy = new EnumMap<>(ConnectionState.class);
			Map<ConnectionState, String[]> stateTypeNames = new EnumMap<>(ConnectionState.class);
			states.forEach((state, ids) -> {
				stateCopy.put(state, Collections.unmodifiableMap(new HashMap<>(ids)));
				stateTypeNames.put(state, indexTypeNames(protocol, state, ids));
			});
			copy.put(protocol, Collections.unmodifiableMap(stateCopy));
			typeNames.put(protocol, Collections.unmodifiableMap(stateTypeNames));
		});
		this.protocols = Collections.unmodifiableMap(copy);
		this.typeNames = Collections.unmodifiableMap(typeNames);
		this.supportedProtocolVersions = Collections.unmodifiableSet(new TreeSet<>(protocols.keySet()));
	}

	public int idOf(ConnectionState connectionState, String typeName, int protocolVersion) {
		Objects.requireNonNull(connectionState, "connectionState");
		Objects.requireNonNull(typeName, "typeName");
		Map<ConnectionState, Map<String, Integer>> states = protocols.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		return states.getOrDefault(connectionState, Collections.emptyMap())
			.getOrDefault(typeName, -1);
	}

	public String typeNameOf(ConnectionState connectionState, int packetId, int protocolVersion) {
		Objects.requireNonNull(connectionState, "connectionState");
		Map<ConnectionState, String[]> states = this.typeNames.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		String[] names = states.get(connectionState);
		return names != null && packetId >= 0 && packetId < names.length ? names[packetId] : null;
	}

	public Map<String, Integer> packetIds(ConnectionState connectionState, int protocolVersion) {
		Objects.requireNonNull(connectionState, "connectionState");
		Map<ConnectionState, Map<String, Integer>> states = this.protocols.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		return states.getOrDefault(connectionState, Collections.emptyMap());
	}

	public Set<Integer> supportedProtocolVersions() {
		return supportedProtocolVersions;
	}

	private static String[] indexTypeNames(int protocolVersion, ConnectionState connectionState, Map<String, Integer> packetIds) {
		int largestPacketId = -1;
		for (int packetId : packetIds.values()) {
			largestPacketId = Math.max(largestPacketId, packetId);
		}

		String[] names = new String[largestPacketId + 1];
		packetIds.forEach((typeName, packetId) -> {
			String previousTypeName = names[packetId];
			if (previousTypeName != null) {
				throw new IllegalArgumentException("Duplicate packet ID " + packetId + " for protocol " + protocolVersion + " in " + connectionState + ": " + previousTypeName + " and " + typeName);
			}
			names[packetId] = typeName;
		});
		return names;
	}
}

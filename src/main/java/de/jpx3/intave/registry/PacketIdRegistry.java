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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.module.linker.packet.PacketId;
import de.jpx3.intave.packet.ConnectionState;

import java.util.*;

public final class PacketIdRegistry<T extends Enum<T> & PacketId> {
	private final Map<Integer, Map<ConnectionState, Map<T, Integer>>> protocols;
	private final Map<Integer, Map<ConnectionState, List<T>>> packetTypes;
	private final Set<Integer> supportedProtocolVersions;

	PacketIdRegistry(Class<T> packetIdClass, Map<Integer, Map<ConnectionState, Map<T, Integer>>> protocols) {
		Map<Integer, Map<ConnectionState, Map<T, Integer>>> copy = new HashMap<>();
		Map<Integer, Map<ConnectionState, List<T>>> packetTypes = new HashMap<>();
		protocols.forEach((protocol, states) -> {
			Map<ConnectionState, Map<T, Integer>> stateCopy = new EnumMap<>(ConnectionState.class);
			Map<ConnectionState, List<T>> statePacketTypes = new EnumMap<>(ConnectionState.class);
			states.forEach((state, ids) -> {
				Map<T, Integer> idCopy = new EnumMap<>(packetIdClass);
				idCopy.putAll(ids);
				stateCopy.put(state, Collections.unmodifiableMap(idCopy));
				statePacketTypes.put(state, indexPacketTypes(protocol, state, ids));
			});
			copy.put(protocol, Collections.unmodifiableMap(stateCopy));
			packetTypes.put(protocol, Collections.unmodifiableMap(statePacketTypes));
		});
		this.protocols = Collections.unmodifiableMap(copy);
		this.packetTypes = Collections.unmodifiableMap(packetTypes);
		this.supportedProtocolVersions = Collections.unmodifiableSet(new TreeSet<>(protocols.keySet()));
	}

	public int idOf(ConnectionState connectionState, PacketId packetType, int protocolVersion) {
		Objects.requireNonNull(connectionState, "connectionState");
		Objects.requireNonNull(packetType, "packetType");
		Map<ConnectionState, Map<T, Integer>> states = protocols.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		return states.getOrDefault(connectionState, Collections.emptyMap())
			.getOrDefault(packetType, -1);
	}

	public @Nullable T typeOf(ConnectionState connectionState, int packetId, int protocolVersion) {
		Objects.requireNonNull(connectionState, "connectionState");
		Map<ConnectionState, List<T>> states = this.packetTypes.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		List<T> types = states.get(connectionState);
		return types != null && packetId >= 0 && packetId < types.size() ? types.get(packetId) : null;
	}

	public Map<T, Integer> packetIds(ConnectionState connectionState, int protocolVersion) {
		Objects.requireNonNull(connectionState, "connectionState");
		Map<ConnectionState, Map<T, Integer>> states = this.protocols.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		return states.getOrDefault(connectionState, Collections.emptyMap());
	}

	public Set<Integer> supportedProtocolVersions() {
		return supportedProtocolVersions;
	}

	private static <T extends PacketId> List<T> indexPacketTypes(int protocolVersion, ConnectionState connectionState, Map<T, Integer> packetIds) {
		int largestPacketId = -1;
		for (int packetId : packetIds.values()) {
			largestPacketId = Math.max(largestPacketId, packetId);
		}

		List<T> types = new ArrayList<>(Collections.nCopies(largestPacketId + 1, null));
		packetIds.forEach((packetType, packetId) -> {
			T previousType = types.set(packetId, packetType);
			if (previousType != null) {
				throw new IllegalArgumentException("Duplicate packet ID " + packetId + " for protocol " + protocolVersion + " in " + connectionState + ": " + previousType + " and " + packetType);
			}
		});
		return Collections.unmodifiableList(types);
	}
}

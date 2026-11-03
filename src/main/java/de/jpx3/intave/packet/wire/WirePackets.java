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

package de.jpx3.intave.packet.wire;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.PacketDirection;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.contract.TypedPacketContract;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class WirePackets {
	private final PacketIdRegistry<?> registry;
	private static final WirePackets SERVERBOUND = new WirePackets(PacketIdRegistries.serverbound());
	private static final WirePackets CLIENTBOUND = new WirePackets(PacketIdRegistries.clientbound());
	private final Int2ObjectMap<Map<ConnectionState, WirePacketDefinition<?>[]>> packets = new Int2ObjectOpenHashMap<>();

	static {
		WirePacketCatalog.registerAll();
	}

	private WirePackets(PacketIdRegistry<?> registry) {
		this.registry = registry;
		for (int protocolVersion : registry.supportedProtocolVersions()) {
			packets.put(protocolVersion, new EnumMap<>(ConnectionState.class));
		}
	}

	private static WirePackets direction(PacketDirection direction) {
		return Objects.requireNonNull(direction, "direction") == PacketDirection.SERVERBOUND ? SERVERBOUND : CLIENTBOUND;
	}

	static <T extends PacketContract> void register(PacketType packetType, Class<T> packetClass) {
		direction(packetType.direction()).registerPacket(packetType, packetClass);
	}

	private <T extends PacketContract> void registerPacket(PacketType packetType, Class<T> packetClass) {
		WirePacketDefinition<T> definition = WirePacketDefinition.from(packetClass);
		T prototype = definition.createEmpty();
		for (int protocolVersion : registry.supportedProtocolVersions()) {
			for (ConnectionState state : ConnectionState.values()) {
				int packetId = registry.idOf(state, packetType, protocolVersion);
				if (packetId >= 0 && prototype.appliesTo(state, packetType, protocolVersion)) {
					register(protocolVersion, state, packetType, packetId, definition);
				}
			}
		}
	}

	private void register(int protocolVersion, ConnectionState state, PacketType packetType, int packetId, WirePacketDefinition<?> definition) {
		Map<ConnectionState, WirePacketDefinition<?>[]> states = packets.get(protocolVersion);
		WirePacketDefinition<?>[] registrations = states.get(state);
		if (registrations == null) {
			registrations = new WirePacketDefinition<?>[packetId + 1];
			states.put(state, registrations);
		} else if (packetId >= registrations.length) {
			registrations = Arrays.copyOf(registrations, packetId + 1);
			states.put(state, registrations);
		}
		if (registrations[packetId] != null) {
			throw new IllegalStateException("Duplicate wire packet " + packetType + " in state " + state + " for protocol version " + protocolVersion);
		}
		registrations[packetId] = definition;
	}

	private <T extends PacketContract> T createPlayPacketInternal(PacketType packetType, int protocolVersion) {
		return createPacketInternal(ConnectionState.PLAY, packetType, protocolVersion);
	}

	private <T extends PacketContract> T createLoginPacketInternal(PacketType packetType, int protocolVersion) {
		return createPacketInternal(ConnectionState.LOGIN, packetType, protocolVersion);
	}

	private <T extends PacketContract> T createStatusPacketInternal(PacketType packetType, int protocolVersion) {
		return createPacketInternal(ConnectionState.STATUS, packetType, protocolVersion);
	}

	private <T extends PacketContract> T createPacketInternal(ConnectionState state, PacketType packetType, int protocolVersion) {
		int packetId = registry.idOf(state, packetType, protocolVersion);
		if (packetId < 0) {
			throw new IllegalArgumentException("No packet with type " + packetType + " found in state " + state + " for protocol version " + protocolVersion);
		}
		return createPacketInternal(state, packetId, protocolVersion);
	}

	private <T extends PacketContract> T createPacketInternal(ConnectionState state, int packetId, int protocolVersion) {
		//noinspection unchecked
		return (T) find(state, packetId, protocolVersion).createEmpty();
	}

	@SuppressWarnings("unchecked")
	private <T extends PacketContract> @Nullable T createVersionIndependentPacketInternal(ConnectionState state, ByteBuf packet) {
		ByteBuf input = packet.duplicate();
		int packetId = ByteBufStreamCodecs.VARINT.decode(input);
		WirePacketDefinition<?> packetType = findVersionIndependentPacketType(state, packetId);
		return packetType == null ? null : (T) packetType.decode(input);
	}

	@SuppressWarnings("unchecked")
	private <T extends PacketContract> @Nullable T decodePacketInternal(ConnectionState state, int protocolVersion, ByteBuf packet) {
		ByteBuf input = packet.duplicate();
		int packetId = ByteBufStreamCodecs.VARINT.decode(input);
		if (!packetIdKnownInternal(state, packetId, protocolVersion)) {
			return null;
		}
		return (T) decodePayload(state, packetId, protocolVersion, input);
	}

	private PacketContract decodePayload(ConnectionState state, int packetId, int protocolVersion, ByteBuf payload) {
		return find(state, packetId, protocolVersion).decode(payload);
	}

	private boolean packetIdKnownInternal(ConnectionState state, int packetId, int protocolVersion) {
		Map<ConnectionState, WirePacketDefinition<?>[]> states = packets.get(protocolVersion);
		if (states == null) {
			return false;
		}
		WirePacketDefinition<?>[] registrations = states.get(state);
		return registrations != null && packetId >= 0 && packetId < registrations.length && registrations[packetId] != null;
	}

	private @Nullable WirePacketDefinition<?> findVersionIndependentPacketType(ConnectionState state, int packetId) {
		WirePacketDefinition<?> packetType = null;
		for (Map<ConnectionState, WirePacketDefinition<?>[]> states : packets.values()) {
			WirePacketDefinition<?>[] registrations = states.get(state);
			if (registrations == null || packetId < 0 || packetId >= registrations.length) {
				return null;
			}
			WirePacketDefinition<?> candidate = registrations[packetId];
			if (candidate == null) {
				return null;
			}
			if (packetType == null) {
				packetType = candidate;
			} else if (packetType != candidate) {
				return null;
			}
		}
		return packetType;
	}

	private WirePacketDefinition<?> find(ConnectionState state, int packetId, int protocolVersion) {
		Objects.requireNonNull(state, "state");
		Map<ConnectionState, WirePacketDefinition<?>[]> states = packets.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		WirePacketDefinition<?>[] registrations = states.get(state);
		if (registrations != null && packetId >= 0 && packetId < registrations.length && registrations[packetId] != null) {
			return registrations[packetId];
		}
		throw new IllegalArgumentException("No wire packet with ID " + packetId + " found in state " + state + " for protocol version " + protocolVersion);
	}

	public static <T extends PacketContract> T createPlayPacket(PacketType packetType, int protocolVersion) {
		return direction(packetType.direction()).createPlayPacketInternal(packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createPlayPacket(PacketDirection direction, PacketType packetType, int protocolVersion) {
		return direction(direction).createPlayPacketInternal(packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createLoginPacket(PacketType packetType, int protocolVersion) {
		return direction(packetType.direction()).createLoginPacketInternal(packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createLoginPacket(PacketDirection direction, PacketType packetType, int protocolVersion) {
		return direction(direction).createLoginPacketInternal(packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createStatusPacket(PacketType packetType, int protocolVersion) {
		return direction(packetType.direction()).createStatusPacketInternal(packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createStatusPacket(PacketDirection direction, PacketType packetType, int protocolVersion) {
		return direction(direction).createStatusPacketInternal(packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createPacket(ConnectionState state, PacketType packetType, int protocolVersion) {
		return direction(packetType.direction()).createPacketInternal(state, packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createPacket(PacketDirection direction, ConnectionState state, PacketType packetType, int protocolVersion) {
		return direction(direction).createPacketInternal(state, packetType, protocolVersion);
	}

	public static <T extends PacketContract> T createPacket(ConnectionState state, int packetId, int protocolVersion) {
		return SERVERBOUND.createPacketInternal(state, packetId, protocolVersion);
	}

	public static <T extends PacketContract> T createPacket(PacketDirection direction, ConnectionState state, int packetId, int protocolVersion) {
		return direction(direction).createPacketInternal(state, packetId, protocolVersion);
	}

	public static <T extends PacketContract> @Nullable T createVersionIndependentPacket(ConnectionState state, ByteBuf packet) {
		return SERVERBOUND.createVersionIndependentPacketInternal(state, packet);
	}

	public static <T extends PacketContract> @Nullable T createVersionIndependentPacket(PacketDirection direction, ConnectionState state, ByteBuf packet) {
		return direction(direction).createVersionIndependentPacketInternal(state, packet);
	}

	public static <T extends PacketContract> @Nullable T decodePacket(ConnectionState state, int protocolVersion, ByteBuf packet) {
		return SERVERBOUND.decodePacketInternal(state, protocolVersion, packet);
	}

	public static <T extends PacketContract> @Nullable T decodePacket(PacketDirection direction, ConnectionState state, int protocolVersion, ByteBuf packet) {
		return direction(direction).decodePacketInternal(state, protocolVersion, packet);
	}

	@Deprecated
	public static boolean packetIdKnown(ConnectionState state, int packetId, int protocolVersion) {
		return SERVERBOUND.packetIdKnownInternal(state, packetId, protocolVersion);
	}

	public static boolean packetIdKnown(PacketDirection direction, ConnectionState state, int packetId, int protocolVersion) {
		return direction(direction).packetIdKnownInternal(state, packetId, protocolVersion);
	}

	public static void encodePacket(ConnectionState state, PacketType packetType, int protocolVersion, PacketContract packet, ByteBuf output) {
		encodePacket(packetType.direction(), state, packetType, protocolVersion, packet, output);
	}

	public static void encodePacket(ConnectionState connectionState, TypedPacketContract tpc, int protocolVersion, ByteBuf newMessage) {
		encodePacket(tpc.type().direction(), connectionState, tpc.type(), protocolVersion, tpc.contract(), newMessage);
	}

	public static void encodePacket(PacketDirection direction, ConnectionState state, PacketType type, int protocolVersion, PacketContract packet, ByteBuf output) {
		WirePackets packets = direction(direction);
		int id = packets.registry.idOf(state, type, protocolVersion);
		WirePacketDefinition<?> definition = packets.find(state, id, protocolVersion);
		int start = output.writerIndex();
		try {
			ByteBufStreamCodecs.VARINT.encode(output, id);
			definition.encode(output, packet);
		} catch (RuntimeException failure) {
			output.writerIndex(start);
			throw failure;
		}
	}
}

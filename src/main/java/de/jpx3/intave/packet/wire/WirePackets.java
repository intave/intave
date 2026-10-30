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
import de.jpx3.intave.packet.contract.Packet;
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
	private final PacketIdRegistry registry;
	private static final WirePackets SERVERBOUND = new WirePackets(PacketIdRegistries.serverbound());
	private static final WirePackets CLIENTBOUND = new WirePackets(PacketIdRegistries.clientbound());
	private final Int2ObjectMap<Map<ConnectionState, WirePacketWrap<?>[]>> packets = new Int2ObjectOpenHashMap<>();

	static {
		WirePacketCatalog.register();
	}

	private WirePackets(PacketIdRegistry registry) {
		this.registry = registry;
		for (int protocolVersion : registry.supportedProtocolVersions()) {
			packets.put(protocolVersion, new EnumMap<>(ConnectionState.class));
		}
	}

	private static WirePackets direction(PacketDirection direction) {
		return Objects.requireNonNull(direction, "direction") == PacketDirection.SERVERBOUND ? SERVERBOUND : CLIENTBOUND;
	}

	static <T extends Packet> void register(PacketDirection direction, Class<T> packetClass) {
		direction(direction).register(packetClass);
	}

	private <T extends Packet> void register(Class<T> packetClass) {
		WirePacketWrap<T> wirePacketWrap = WirePacketWrap.from(packetClass);
		T prototype = wirePacketWrap.createEmpty();
		for (int protocolVersion : registry.supportedProtocolVersions()) {
			for (ConnectionState state : ConnectionState.values()) {
				Map<String, Integer> nameToId = registry.packetIds(state, protocolVersion);
				for (Map.Entry<String, Integer> packet : nameToId.entrySet()) {
					String name = packet.getKey();
					if (prototype.appliesTo(state, name, protocolVersion)) {
						register(protocolVersion, state, name, packet.getValue(), wirePacketWrap);
					}
				}
			}
		}
	}

	private void register(int protocolVersion, ConnectionState state, String name, int packetId, WirePacketWrap<?> definition) {
		Map<ConnectionState, WirePacketWrap<?>[]> states = packets.get(protocolVersion);
		WirePacketWrap<?>[] registrations = states.get(state);
		if (registrations == null) {
			registrations = new WirePacketWrap<?>[packetId + 1];
			states.put(state, registrations);
		} else if (packetId >= registrations.length) {
			registrations = Arrays.copyOf(registrations, packetId + 1);
			states.put(state, registrations);
		}
		if (registrations[packetId] != null) {
			throw new IllegalStateException("Duplicate wire packet " + name + " in state " + state + " for protocol version " + protocolVersion);
		}
		registrations[packetId] = definition;
	}

	private <T extends Packet> T createPlayPacketInternal(String name, int protocolVersion) {
		return createPacketInternal(ConnectionState.PLAY, name, protocolVersion);
	}

	private <T extends Packet> T createLoginPacketInternal(String name, int protocolVersion) {
		return createPacketInternal(ConnectionState.LOGIN, name, protocolVersion);
	}

	private <T extends Packet> T createStatusPacketInternal(String name, int protocolVersion) {
		return createPacketInternal(ConnectionState.STATUS, name, protocolVersion);
	}

	private <T extends Packet> T createPacketInternal(ConnectionState state, String name, int protocolVersion) {
		int packetId = registry.idOf(state, name, protocolVersion);
		if (packetId < 0) {
			throw new IllegalArgumentException("No packet with name " + name + " found in state " + state + " for protocol version " + protocolVersion);
		}
		return createPacketInternal(state, packetId, protocolVersion);
	}

	private <T extends Packet> T createPacketInternal(ConnectionState state, int packetId, int protocolVersion) {
		//noinspection unchecked
		return (T) find(state, packetId, protocolVersion).createEmpty();
	}

	@SuppressWarnings("unchecked")
	private <T extends Packet> @Nullable T createVersionIndependentPacketInternal(ConnectionState state, ByteBuf packet) {
		ByteBuf input = packet.duplicate();
		int packetId = ByteBufStreamCodecs.VARINT.decode(input);
		WirePacketWrap<?> packetType = findVersionIndependentPacketType(state, packetId);
		return packetType == null ? null : (T) packetType.decode(input);
	}

	@SuppressWarnings("unchecked")
	private <T extends Packet> @Nullable T decodePacketInternal(ConnectionState state, int protocolVersion, ByteBuf packet) {
		ByteBuf input = packet.duplicate();
		int packetId = ByteBufStreamCodecs.VARINT.decode(input);
		if (!packetIdKnownInternal(state, packetId, protocolVersion)) {
			return null;
		}
		return (T) decodePayload(state, packetId, protocolVersion, input);
	}

	private Packet decodePayload(ConnectionState state, int packetId, int protocolVersion, ByteBuf payload) {
		return find(state, packetId, protocolVersion).decode(payload);
	}

	private boolean packetIdKnownInternal(ConnectionState state, int packetId, int protocolVersion) {
		Map<ConnectionState, WirePacketWrap<?>[]> states = packets.get(protocolVersion);
		if (states == null) {
			return false;
		}
		WirePacketWrap<?>[] registrations = states.get(state);
		return registrations != null && packetId >= 0 && packetId < registrations.length && registrations[packetId] != null;
	}

	private @Nullable WirePacketWrap<?> findVersionIndependentPacketType(ConnectionState state, int packetId) {
		WirePacketWrap<?> packetType = null;
		for (Map<ConnectionState, WirePacketWrap<?>[]> states : packets.values()) {
			WirePacketWrap<?>[] registrations = states.get(state);
			if (registrations == null || packetId < 0 || packetId >= registrations.length) {
				return null;
			}
			WirePacketWrap<?> candidate = registrations[packetId];
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

	private WirePacketWrap<?> find(ConnectionState state, int packetId, int protocolVersion) {
		Objects.requireNonNull(state, "state");
		Map<ConnectionState, WirePacketWrap<?>[]> states = packets.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		WirePacketWrap<?>[] registrations = states.get(state);
		if (registrations != null && packetId >= 0 && packetId < registrations.length && registrations[packetId] != null) {
			return registrations[packetId];
		}
		throw new IllegalArgumentException("No wire packet with ID " + packetId + " found in state " + state + " for protocol version " + protocolVersion);
	}

	public static <T extends Packet> T createPlayPacket(String name, int protocolVersion) {
		return SERVERBOUND.createPlayPacketInternal(name, protocolVersion);
	}

	public static <T extends Packet> T createPlayPacket(PacketDirection direction, String name, int protocolVersion) {
		return direction(direction).createPlayPacketInternal(name, protocolVersion);
	}

	public static <T extends Packet> T createLoginPacket(String name, int protocolVersion) {
		return SERVERBOUND.createLoginPacketInternal(name, protocolVersion);
	}

	public static <T extends Packet> T createLoginPacket(PacketDirection direction, String name, int protocolVersion) {
		return direction(direction).createLoginPacketInternal(name, protocolVersion);
	}

	public static <T extends Packet> T createStatusPacket(String name, int protocolVersion) {
		return SERVERBOUND.createStatusPacketInternal(name, protocolVersion);
	}

	public static <T extends Packet> T createStatusPacket(PacketDirection direction, String name, int protocolVersion) {
		return direction(direction).createStatusPacketInternal(name, protocolVersion);
	}

	public static <T extends Packet> T createPacket(ConnectionState state, String name, int protocolVersion) {
		return SERVERBOUND.createPacketInternal(state, name, protocolVersion);
	}

	public static <T extends Packet> T createPacket(PacketDirection direction, ConnectionState state, String name, int protocolVersion) {
		return direction(direction).createPacketInternal(state, name, protocolVersion);
	}

	public static <T extends Packet> T createPacket(ConnectionState state, int packetId, int protocolVersion) {
		return SERVERBOUND.createPacketInternal(state, packetId, protocolVersion);
	}

	public static <T extends Packet> T createPacket(PacketDirection direction, ConnectionState state, int packetId, int protocolVersion) {
		return direction(direction).createPacketInternal(state, packetId, protocolVersion);
	}

	public static <T extends Packet> @Nullable T createVersionIndependentPacket(ConnectionState state, ByteBuf packet) {
		return SERVERBOUND.createVersionIndependentPacketInternal(state, packet);
	}

	public static <T extends Packet> @Nullable T createVersionIndependentPacket(PacketDirection direction, ConnectionState state, ByteBuf packet) {
		return direction(direction).createVersionIndependentPacketInternal(state, packet);
	}

	public static <T extends Packet> @Nullable T decodePacket(ConnectionState state, int protocolVersion, ByteBuf packet) {
		return SERVERBOUND.decodePacketInternal(state, protocolVersion, packet);
	}

	public static <T extends Packet> @Nullable T decodePacket(PacketDirection direction, ConnectionState state, int protocolVersion, ByteBuf packet) {
		return direction(direction).decodePacketInternal(state, protocolVersion, packet);
	}

	public static boolean packetIdKnown(ConnectionState state, int packetId, int protocolVersion) {
		return SERVERBOUND.packetIdKnownInternal(state, packetId, protocolVersion);
	}

	public static boolean packetIdKnown(PacketDirection direction, ConnectionState state, int packetId, int protocolVersion) {
		return direction(direction).packetIdKnownInternal(state, packetId, protocolVersion);
	}

	/**
	 * Writes a packet ID followed by its payload; framing and compression belong to the pipeline.
	 */
	public static void encodePacket(PacketDirection direction, ConnectionState state, String name, int protocolVersion, Packet packet, ByteBuf output) {
		WirePackets packets = direction(direction);
		int id = packets.registry.idOf(state, name, protocolVersion);
		WirePacketWrap<?> definition = packets.find(state, id, protocolVersion);
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

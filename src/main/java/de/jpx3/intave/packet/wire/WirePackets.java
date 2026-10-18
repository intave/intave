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
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.codec.VarIntCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.Packet;
import de.jpx3.intave.registry.PacketIdRegistries;
import de.jpx3.intave.registry.PacketIdRegistry;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class WirePackets {
	private static final PacketIdRegistry REGISTRY = PacketIdRegistries.serverbound();
	private static final Int2ObjectMap<Map<ConnectionState, WirePacketRegistration<?>[]>> PACKETS = new Int2ObjectOpenHashMap<>();

	static {
		for (int protocolVersion : REGISTRY.supportedProtocolVersions()) {
			PACKETS.put(protocolVersion, new EnumMap<>(ConnectionState.class));
		}
		registerWirePacket(HandshakeWirePacket.class);
		registerWirePacket(PlayerInputWirePacket.class);
	}

	private WirePackets() {
	}

	private static <T extends Packet> void registerWirePacket(Class<T> packetClass) {
		WirePacketRegistration<T> wirePacketRegistration = WirePacketRegistration.of(packetClass);
		T prototype = wirePacketRegistration.create();
		for (int protocolVersion : REGISTRY.supportedProtocolVersions()) {
			for (ConnectionState state : ConnectionState.values()) {
				Map<String, Integer> nameToId = REGISTRY.packetIds(state, protocolVersion);
				for (Map.Entry<String, Integer> packet : nameToId.entrySet()) {
					String name = packet.getKey();
					if (prototype.appliesTo(state, name, protocolVersion)) {
						storePacketType(protocolVersion, state, name, packet.getValue(), wirePacketRegistration);
					}
				}
			}
		}
	}

	private static void storePacketType(
		int protocolVersion, ConnectionState state, String name, int packetId,
		WirePacketRegistration<?> wirePacketRegistration
	) {
		Map<ConnectionState, WirePacketRegistration<?>[]> states = PACKETS.get(protocolVersion);
		WirePacketRegistration<?>[] registrations = states.get(state);
		if (registrations == null) {
			registrations = new WirePacketRegistration<?>[packetId + 1];
			states.put(state, registrations);
		} else if (packetId >= registrations.length) {
			registrations = Arrays.copyOf(registrations, packetId + 1);
			states.put(state, registrations);
		}
		if (registrations[packetId] != null) {
			throw new IllegalStateException("Duplicate wire packet " + name + " in state " + state + " for protocol version " + protocolVersion);
		}
		registrations[packetId] = wirePacketRegistration;
	}

	public static <T extends Packet> T createPlayPacket(String name, int protocolVersion) {
		return createPacket(ConnectionState.PLAY, name, protocolVersion);
	}

	public static <T extends Packet> T createPacket(ConnectionState state, String name, int protocolVersion) {
		int packetId = REGISTRY.idOf(state, name, protocolVersion);
		if (packetId < 0) {
			throw new IllegalArgumentException("No packet with name " + name + " found in state " + state + " for protocol version " + protocolVersion);
		}
		return createPacket(state, packetId, protocolVersion);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Packet> T createPacket(ConnectionState state, int packetId, int protocolVersion) {
		return (T) packetType(state, packetId, protocolVersion).create();
	}

	@SuppressWarnings("unchecked")
	public static <T extends Packet> @Nullable T decodePacket(ConnectionState state, ByteBuf packet) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(packet, "packet");
		ByteBuf input = packet.duplicate();
		int packetId = VarIntCodec.INSTANCE.decode(input);
		WirePacketRegistration<?> packetType = versionIndependentPacketType(state, packetId);
		return packetType == null ? null : (T) packetType.decode(input);
	}

	@SuppressWarnings("unchecked")
	public static <T extends Packet> @Nullable T decodePacket(
		ConnectionState state,
		int protocolVersion,
		ByteBuf packet
	) {
		Objects.requireNonNull(packet, "packet");
		ByteBuf input = packet.duplicate();
		int packetId = VarIntCodec.INSTANCE.decode(input);
		if (!packetIdKnown(state, packetId, protocolVersion)) {
			return null;
		}
		return (T) decodePayload(state, packetId, protocolVersion, input);
	}

	private static Packet decodePayload(
		ConnectionState state, int packetId,
		int protocolVersion, ByteBuf payload
	) {
		Objects.requireNonNull(payload, "payload");
		return packetType(state, packetId, protocolVersion).decode(payload);
	}

	public static boolean packetIdKnown(ConnectionState state, int packetId, int protocolVersion) {
		Map<ConnectionState, WirePacketRegistration<?>[]> states = PACKETS.get(protocolVersion);
		if (states == null) {
			return false;
		}
		WirePacketRegistration<?>[] registrations = states.get(state);
		return registrations != null && packetId >= 0 && packetId < registrations.length && registrations[packetId] != null;
	}

	private static @Nullable WirePacketRegistration<?> versionIndependentPacketType(
		ConnectionState state,
		int packetId
	) {
		WirePacketRegistration<?> packetType = null;
		for (Map<ConnectionState, WirePacketRegistration<?>[]> states : PACKETS.values()) {
			WirePacketRegistration<?>[] registrations = states.get(state);
			if (registrations == null || packetId < 0 || packetId >= registrations.length) {
				return null;
			}
			WirePacketRegistration<?> candidate = registrations[packetId];
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

	private static WirePacketRegistration<?> packetType(ConnectionState state, int packetId, int protocolVersion) {
		Objects.requireNonNull(state, "state");
		Map<ConnectionState, WirePacketRegistration<?>[]> states = PACKETS.get(protocolVersion);
		if (states == null) {
			throw new IllegalArgumentException("Unsupported packet protocol version " + protocolVersion);
		}
		WirePacketRegistration<?>[] registrations = states.get(state);
		if (registrations != null && packetId >= 0 && packetId < registrations.length && registrations[packetId] != null) {
			return registrations[packetId];
		}
		throw new IllegalArgumentException("No wire packet with ID " + packetId + " found in state " + state + " for protocol version " + protocolVersion);
	}

	private static final class WirePacketRegistration<T extends Packet> {
		private final Class<T> packetClass;
		private final Constructor<T> constructor;
		private final StreamCodec<ByteBuf, ByteBuf, T> codec;

		private WirePacketRegistration(
			Class<T> packetClass,
			Constructor<T> constructor,
			StreamCodec<ByteBuf, ByteBuf, T> codec
		) {
			this.packetClass = packetClass;
			this.constructor = constructor;
			this.codec = codec;
		}

		private T create() {
			try {
				return this.constructor.newInstance();
			} catch (ReflectiveOperationException failure) {
				throw new IllegalStateException("Could not create " + this.packetClass.getName(), failure);
			}
		}

		private T decode(ByteBuf payload) {
			return this.packetClass.cast(this.codec.decode(payload));
		}

		private static <T extends Packet> WirePacketRegistration<T> of(Class<T> packetClass) {
			Objects.requireNonNull(packetClass, "packetClass");
			try {
				Constructor<T> constructor = packetClass.getConstructor();
				StreamCodec<ByteBuf, ByteBuf, T> codec = findCodecIn(packetClass);
				return new WirePacketRegistration<>(packetClass, constructor, codec);
			} catch (ReflectiveOperationException failure) {
				throw new IllegalArgumentException(
					packetClass.getName() + " must have a public no-argument constructor and STREAM_CODEC field", failure
				);
			}
		}

		@SuppressWarnings("unchecked")
		private static <T extends Packet> @NonNull StreamCodec<ByteBuf, ByteBuf, T> findCodecIn(Class<T> packetClass) throws NoSuchFieldException, IllegalAccessException {
			Field codecField = packetClass.getField("STREAM_CODEC");
			if (!Modifier.isStatic(codecField.getModifiers())
				|| !StreamCodec.class.isAssignableFrom(codecField.getType())) {
				throw new IllegalArgumentException(packetClass.getName() + ".STREAM_CODEC is not a static StreamCodec");
			}
			StreamCodec<ByteBuf, ByteBuf, T> codec = (StreamCodec<ByteBuf, ByteBuf, T>) codecField.get(null);
			if (codec == null) {
				throw new IllegalArgumentException(packetClass.getName() + ".STREAM_CODEC is null");
			}
			return codec;
		}
	}
}

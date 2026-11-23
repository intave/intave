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

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.PacketContract;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Objects;

final class WirePacketDefinition<T extends PacketContract> {
	private final Class<T> packetClass;
	private final Constructor<T> constructor;
	private final StreamCodec<ByteBuf, ByteBuf, T> codec;

	private WirePacketDefinition(
		Class<T> packetClass,
		Constructor<T> constructor,
		StreamCodec<ByteBuf, ByteBuf, T> codec
	) {
		this.packetClass = Objects.requireNonNull(packetClass, "packetClass");
		this.constructor = Objects.requireNonNull(constructor, "constructor");
		this.codec = Objects.requireNonNull(codec, "codec");
	}

	public T createEmpty() {
		try {
			return this.constructor.newInstance();
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not create " + this.packetClass.getName(), failure);
		}
	}

	public T decode(ByteBuf payload) {
		return this.codec.decode(payload);
	}

	public void encode(ByteBuf payload, PacketContract packet) {
		if (packet.getClass() != this.packetClass) {
			throw new IllegalArgumentException(
				String.format("Expected %s, got %s", this.packetClass.getName(), packet.getClass().getName())
			);
		}
		this.codec.encode(payload, this.packetClass.cast(packet));
	}

	public Class<T> packetClass() {
		return packetClass;
	}

	public static <T extends PacketContract> WirePacketDefinition<T> from(Class<T> packetClass) {
		try {
			Constructor<T> constructor = packetClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			Field codecField = packetClass.getDeclaredField("STREAM_CODEC");
			codecField.setAccessible(true);
			@SuppressWarnings("unchecked")
			StreamCodec<ByteBuf, ByteBuf, T> codec = (StreamCodec<ByteBuf, ByteBuf, T>) codecField.get(null);
			return new WirePacketDefinition<>(packetClass, constructor, codec);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalArgumentException(
				String.format("%s must declare a no-argument constructor and STREAM_CODEC field", packetClass.getName()),
				failure
			);
		}
	}
}

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
import de.jpx3.intave.packet.contract.Packet;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Constructor;
import java.util.Objects;

final class WirePacketWrap<T extends Packet> {
	private final Class<T> packetClass;
	private final Constructor<T> constructor;
	private final StreamCodec<ByteBuf, ByteBuf, T> codec;

	private WirePacketWrap(
			Class<T> packetClass,
			Constructor<T> constructor,
			StreamCodec<ByteBuf, ByteBuf, T> codec) {
		this.packetClass = packetClass;
		this.constructor = constructor;
		this.codec = codec;
	}

	public T createEmpty() {
		try {
			return this.constructor.newInstance();
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(
					"Could not create " + this.packetClass.getName(), failure);
		}
	}

	public T decode(ByteBuf payload) {
		return this.codec.decode(payload);
	}

	public void encode(ByteBuf payload, Packet packet) {
		if (packet.getClass() != this.packetClass) {
			throw new IllegalArgumentException(
					"Expected "
							+ this.packetClass.getName()
							+ ", got "
							+ packet.getClass().getName());
		}
		this.codec.encode(payload, this.packetClass.cast(packet));
	}

	public static <T extends Packet> WirePacketWrap<T> from(Class<T> packetClass) {
		Objects.requireNonNull(packetClass, "packetClass");
		try {
			Constructor<T> constructor = packetClass.getConstructor();
			@SuppressWarnings("unchecked")
			StreamCodec<ByteBuf, ByteBuf, T> codec =
					(StreamCodec<ByteBuf, ByteBuf, T>) packetClass.getDeclaredField("STREAM_CODEC").get(null);
			return new WirePacketWrap<>(packetClass, constructor, codec);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalArgumentException(
					packetClass.getName()
							+ " must declare a public no-argument constructor and public STREAM_CODEC"
							+ " field",
					failure);
		}
	}
}

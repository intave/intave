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

package de.jpx3.intave.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class StreamCodecTest {
	private static final StreamCodec<ByteBuf, ByteBuf, Example> VERSION_ONE = ByteBufStreamCodecs.smartCodec(
		codec -> codec.field("name", ByteBufStreamCodecs.STRING, Example::name),
		values -> new Example(values.stringValue("name"), 0, false)
	);

	private static final StreamCodec<ByteBuf, ByteBuf, Example> VERSION_TWO = ByteBufStreamCodecs.smartCodec(
		codec -> codec
			.field("name", ByteBufStreamCodecs.STRING, Example::name)
			.field("score", ByteBufStreamCodecs.INTEGER, Example::score, 20)
			.field("enabled", ByteBufStreamCodecs.BOOLEAN, Example::enabled, true),
		values -> new Example(
			values.stringValue("name"),
			values.integerValue("score"),
			values.booleanValue("enabled")
		)
	);

	private static final StreamCodec<ByteBuf, ByteBuf, Example> SCORE_REQUIRED = ByteBufStreamCodecs.smartCodec(
		codec -> codec
			.field("name", ByteBufStreamCodecs.STRING, Example::name)
			.field("score", ByteBufStreamCodecs.INTEGER, Example::score),
		values -> new Example(
			values.stringValue("name"),
			values.integerValue("score"),
			false
		)
	);
	private static final StreamCodec<ByteBuf, ByteBuf, ReflectedExample> REFLECTED = ByteBufStreamCodecs
		.smartReflectionCodecBuilder(ReflectedExample.class)
		.field("name", ByteBufStreamCodecs.STRING)
		.field("score", ByteBufStreamCodecs.INTEGER, () -> 10)
		.build();

	@Test
	void canNotBeNullRejectsNullBeforeCallingEncoder() {
		StreamCodec<String, StringBuilder, String> codec = StreamCodec
			.<String, StringBuilder, String>of(StringBuilder::append, value -> value)
			.throwIfNullEncoded();
		StringBuilder output = new StringBuilder("prefix");
		assertThrows(IllegalArgumentException.class, () -> codec.encode(output, null));
		assertEquals("prefix", output.toString());

		String value = "value";
		codec.encode(output, value);
		assertEquals("prefixvalue", output.toString());
		assertSame(value, codec.decode(value));
	}

	@Test
	void smartCodecReadsOlderPayloadWithFallbacks() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			VERSION_ONE.encode(buffer, new Example("alpha", 12, false));
			Example decoded = VERSION_TWO.decode(buffer);
			assertEquals(new Example("alpha", 20, true), decoded);
		} finally {
			buffer.release();
		}
	}

	@Test
	void smartCodecSkipsNewerUnknownFields() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			VERSION_TWO.encode(buffer, new Example("beta", 42, true));
			Example decoded = VERSION_ONE.decode(buffer);
			assertEquals(new Example("beta", 0, false), decoded);
		} finally {
			buffer.release();
		}
	}

	@Test
	void smartCodecKeepsRequiredFieldsStrict() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			VERSION_ONE.encode(buffer, new Example("gamma", 12, false));
			assertThrows(IllegalStateException.class, () -> SCORE_REQUIRED.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	@Test
	void reflectionSmartCodecUsesPrivateFieldsAndConstructor() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			ReflectedExample expected = new ReflectedExample("delta", 64);
			REFLECTED.encode(buffer, expected);
			ReflectedExample decoded = REFLECTED.decode(buffer);
			assertEquals(expected, decoded);
		} finally {
			buffer.release();
		}
	}

	@Test
	void materialCodecMapsLegacyWoodDoorToOakDoor() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			ByteBufStreamCodecs.STRING.encode(buffer, "WOOD_DOOR");
			assertEquals(Material.OAK_DOOR, ByteBufStreamCodecs.MATERIAL.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	@Test
	void dispatchBuilderCodecRoundTripsSubtype() {
		StreamCodec<ByteBuf, ByteBuf, DispatchExample> codec =
			StreamCodec.dispatchBuilder(DispatchExample.class, ByteBufStreamCodecs.UNSIGNED_BYTE)
				.subtype(1, DispatchLeft.class, DispatchLeft.STREAM_CODEC)
				.subtype(2, DispatchRight.class, DispatchRight.STREAM_CODEC)
				.build();

		ByteBuf buffer = Unpooled.buffer();
		try {
			DispatchRight expected = new DispatchRight(37);
			codec.encode(buffer, expected);
			assertEquals(expected, codec.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	@Test
	void dispatchBuilderCodecRejectsUnknownKey() {
		StreamCodec<ByteBuf, ByteBuf, DispatchExample> codec =
			StreamCodec.dispatchBuilder(DispatchExample.class, ByteBufStreamCodecs.UNSIGNED_BYTE)
				.subtype(1, DispatchLeft.class, DispatchLeft.STREAM_CODEC)
				.build();

		ByteBuf buffer = Unpooled.buffer();
		try {
			buffer.writeByte(2);

			assertThrows(IllegalStateException.class, () -> codec.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	private record Example(String name, int score, boolean enabled) {}

	private record ReflectedExample(String name, int score) {}

	private interface DispatchExample {}

	private static final class DispatchLeft implements DispatchExample {
		private static final DispatchLeft INSTANCE = new DispatchLeft();
		private static final StreamCodec<ByteBuf, ByteBuf, DispatchLeft> STREAM_CODEC = StreamCodec.of(INSTANCE);

		@Override
		public boolean equals(Object obj) {
			return obj instanceof DispatchLeft;
		}

		@Override
		public int hashCode() {
			return DispatchLeft.class.hashCode();
		}
	}

	private record DispatchRight(int value) implements DispatchExample {
		private static final StreamCodec<ByteBuf, ByteBuf, DispatchRight> STREAM_CODEC = StreamCodec.compound(
			ByteBufStreamCodecs.INTEGER,
			DispatchRight::value,
			DispatchRight::new
		);

		@Override
		public boolean equals(
			Object obj
		) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			DispatchRight that = (DispatchRight) obj;
			return value == that.value;
		}
	}
}

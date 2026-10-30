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

import de.jpx3.intave.codec.smart.ReflectionSmartCodecBuilder;
import de.jpx3.intave.codec.smart.SmartCodecBuilder;
import de.jpx3.intave.codec.smart.SmartCodecDecodeResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.bukkit.Material;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public final class ByteBufStreamCodecs {
	public static final StreamCodec<ByteBuf, ByteBuf, Byte> BYTE = StreamCodec.of((buf, value) -> buf.writeByte(value), ByteBuf::readByte);
	public static final StreamCodec<ByteBuf, ByteBuf, Short> SHORT = StreamCodec.of((buf, value) -> buf.writeShort(value), ByteBuf::readShort);
	public static final StreamCodec<ByteBuf, ByteBuf, Integer> SIGNED_BYTE = BYTE.beforeAndAfter(Byte::intValue, value -> (byte) (int) value);
	public static final StreamCodec<ByteBuf, ByteBuf, Integer> SIGNED_SHORT = SHORT.beforeAndAfter(Short::intValue, value -> (short) (int) value);
	public static final StreamCodec<ByteBuf, ByteBuf, Integer> UNSIGNED_SHORT = StreamCodec.of(ByteBuf::writeShort, ByteBuf::readUnsignedShort);
	public static final StreamCodec<ByteBuf, ByteBuf, Boolean> BOOLEAN = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
	public static final StreamCodec<ByteBuf, ByteBuf, Integer> UNSIGNED_BYTE = StreamCodec.of(ByteBuf::writeByte, buf -> (int) buf.readUnsignedByte());
	public static final StreamCodec<ByteBuf, ByteBuf, Integer> INTEGER = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
	public static final StreamCodec<ByteBuf, ByteBuf, Integer> VARINT = VarIntCodec.INSTANCE;
	public static final StreamCodec<ByteBuf, ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
	public static final StreamCodec<ByteBuf, ByteBuf, Instant> INSTANT = LONG.beforeAndAfter(Instant::ofEpochMilli, Instant::toEpochMilli);
	public static final StreamCodec<ByteBuf, ByteBuf, Long> VARLONG = VarLongCodec.INSTANCE;
	public static final StreamCodec<ByteBuf, ByteBuf, Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
	public static final StreamCodec<ByteBuf, ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
	public static final StreamCodec<ByteBuf, ByteBuf, Double> FLOAT_AS_DOUBLE = FLOAT.beforeAndAfter(Float::doubleValue, Double::floatValue);
	public static final StreamCodec<ByteBuf, ByteBuf, String> VARINT_STRING = stringCodec(32767);
	public static final StreamCodec<ByteBuf, ByteBuf, byte[]> VARINT_BYTES = varIntByteArray(Integer.MAX_VALUE);
	public static final StreamCodec<ByteBuf, ByteBuf, byte[]> BYTES = StreamCodec.of((buf, arr) -> {
		INTEGER.encode(buf, arr.length);
		buf.writeBytes(arr);
	}, buf -> {
		int length = INTEGER.decode(buf);
		byte[] arr = new byte[length];
		buf.readBytes(arr);
		return arr;
	});
	public static final StreamCodec<ByteBuf, ByteBuf, String> STRING = BYTES.beforeAndAfter(String::new, String::getBytes);
	public static final StreamCodec<ByteBuf, ByteBuf, Material> MATERIAL = STRING.beforeAndAfter(ByteBufStreamCodecs::findOrThrow, Material::name);
	public static final StreamCodec<ByteBuf, ByteBuf, UUID> UUID = StreamCodec.of((buf, uuid) -> {
		buf.writeLong(uuid.getMostSignificantBits());
		buf.writeLong(uuid.getLeastSignificantBits());
	}, buf -> new UUID(buf.readLong(), buf.readLong()));

	public static <T> StreamCodec<ByteBuf, ByteBuf, List<T>> listCodecOf(StreamCodec<ByteBuf, ByteBuf, T> elementCodec) {
		return elementCodec.toListCodec(ByteBufStreamCodecs.INTEGER);
	}

	public static StreamCodec<ByteBuf, ByteBuf, String> stringCodec(int maximum) {
		if (maximum < 0 || maximum > Integer.MAX_VALUE / 3) {
			throw new IllegalArgumentException("Invalid string character limit: " + maximum);
		}
		return StreamCodec.of((output, value) -> {
			byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			if (value.length() > maximum || bytes.length > maximum * 3) {
				throw new IllegalArgumentException("Wire string exceeds " + maximum + " characters");
			}
			VARINT.encode(output, bytes.length);
			output.writeBytes(bytes);
		}, input -> {
			int length = VARINT.decode(input);
			if (length < 0 || length > maximum * 3 || length > input.readableBytes()) {
				throw new IllegalArgumentException("Invalid wire string byte length: " + length);
			}
			byte[] bytes = new byte[length];
			input.readBytes(bytes);
			String value = new String(bytes, StandardCharsets.UTF_8);
			if (value.length() > maximum) {
				throw new IllegalArgumentException("Wire string exceeds " + maximum + " characters");
			}
			return value;
		});
	}

	public static StreamCodec<ByteBuf, ByteBuf, byte[]> varIntByteArray(int maxLength) {
		if (maxLength < 0) throw new IllegalArgumentException("Negative byte array limit");
		return StreamCodec.of((output, value) -> {
			if (value.length > maxLength) {
				throw new IllegalArgumentException("Byte array exceeds " + maxLength + " bytes");
			}
			VARINT.encode(output, value.length);
			output.writeBytes(value);
		}, input -> {
			int length = VARINT.decode(input);
			if (length < 0 || length > maxLength || length > input.readableBytes()) {
				throw new IllegalArgumentException("Invalid byte array length: " + length);
			}
			byte[] value = new byte[length];
			input.readBytes(value);
			return value;
		});
	}

	public static StreamCodec<ByteBuf, ByteBuf, byte[]> fixedByteArray(int length) {
		if (length < 0) throw new IllegalArgumentException("Negative byte array length");
		return StreamCodec.of((output, value) -> {
			if (value.length != length) {
				throw new IllegalArgumentException("Expected exactly " + length + " bytes");
			}
			output.writeBytes(value);
		}, input -> {
			if (input.readableBytes() < length) {
				throw new IllegalArgumentException("Expected exactly " + length + " bytes");
			}
			byte[] value = new byte[length];
			input.readBytes(value);
			return value;
		});
	}

	public static StreamCodec<ByteBuf, ByteBuf, BitSet> fixedBitSet(int bitCount) {
		if (bitCount < 0) throw new IllegalArgumentException("Negative bit count");
		int byteCount = (int) ((bitCount + 7L) / 8);
		return fixedByteArray(byteCount).beforeAndAfter(bytes -> {
			BitSet value = BitSet.valueOf(bytes);
			if (value.length() > bitCount) {
				throw new IllegalArgumentException("Bit set exceeds " + bitCount + " bits");
			}
			return value;
		}, value -> {
			if (value.length() > bitCount) {
				throw new IllegalArgumentException("Bit set exceeds " + bitCount + " bits");
			}
			return Arrays.copyOf(value.toByteArray(), byteCount);
		});
	}

	public static <T extends Enum<T>> StreamCodec<ByteBuf, ByteBuf, T> enumCodec(T[] values) {
		return enumCodec(values, VARINT);
	}

	public static <T extends Enum<T>> StreamCodec<ByteBuf, ByteBuf, T> enumCodec(T[] values, StreamCodec<ByteBuf, ByteBuf, Integer> indexCodec) {
		return indexCodec.beforeAndAfter(index -> {
			if (index < 0 || index >= values.length) {
				throw new IllegalArgumentException("Invalid wire enum index: " + index);
			}
			return values[index];
		}, Enum::ordinal);
	}

	public static <T> StreamCodec<ByteBuf, ByteBuf, List<T>> listCodecOf(StreamCodec<ByteBuf, ByteBuf, T> elementCodec, Function<Integer, List<T>> listFactory) {
		return elementCodec.toListCodec(ByteBufStreamCodecs.INTEGER, listFactory);
	}

	public static <K, V> StreamCodec<ByteBuf, ByteBuf, Map<K, V>> mapCodec(StreamCodec<ByteBuf, ByteBuf, K> keyCodec, StreamCodec<ByteBuf, ByteBuf, V> valueCodec) {
		return StreamCodec.mapCodec(keyCodec, valueCodec, ByteBufStreamCodecs.INTEGER);
	}

	public static StreamCodec<ByteBuf, ByteBuf, byte[]> limitedByteArray(int maxLength) {
		return StreamCodec.of((buf, arr) -> {
			if (arr.length > maxLength) {
				throw new IllegalArgumentException("Byte array length exceeds maximum length of " + maxLength);
			}
			INTEGER.encode(buf, arr.length);
			buf.writeBytes(arr);
		}, buf -> {
			int length = INTEGER.decode(buf);
			if (length > maxLength) {
				throw new IllegalArgumentException("Byte array length exceeds maximum length of " + maxLength);
			}
			byte[] arr = new byte[length];
			buf.readBytes(arr);
			return arr;
		});
	}

	// optional
	public static <T> StreamCodec<ByteBuf, ByteBuf, T> optionalCodecOf(StreamCodec<ByteBuf, ByteBuf, T> elementCodec) {
		return StreamCodec.of((buf, value) -> {
			if (value == null) {
				buf.writeBoolean(false);
			} else {
				buf.writeBoolean(true);
				elementCodec.encode(buf, value);
			}
		}, buf -> {
			boolean present = buf.readBoolean();
			if (!present) {
				return null;
			}
			return elementCodec.decode(buf);
		});
	}

	public static <T> SmartCodecBuilder<ByteBuf, ByteBuf, T> smartCodec() {
		return StreamCodec.smartCodec(INTEGER, STRING, BYTES, Unpooled::buffer, ByteBufStreamCodecs::copyAndRelease, Unpooled::wrappedBuffer, ReferenceCounted::release);
	}

	public static <T> StreamCodec<ByteBuf, ByteBuf, T> smartCodec(Function<SmartCodecBuilder<ByteBuf, ByteBuf, T>, SmartCodecBuilder<ByteBuf, ByteBuf, T>> schema, Function<SmartCodecDecodeResult, T> constructor) {
		return schema.apply(smartCodec()).build(constructor);
	}

	public static <T> ReflectionSmartCodecBuilder<ByteBuf, ByteBuf, T> smartReflectionCodecBuilder(Class<T> type) {
		return ByteBufStreamCodecs.<T>smartCodec().reflectionBuilderOn(type);
	}

	private final static Map<String, String> MATERIAL_ALIASES = new HashMap<>();

	static {
		MATERIAL_ALIASES.put("STATIONARY_WATER", "WATER");
		MATERIAL_ALIASES.put("STATIONARY_LAVA", "LAVA");
		MATERIAL_ALIASES.put("WOOD_DOOR", "OAK_DOOR");
	}

	private static Material findOrThrow(String name) {
		Material material = Material.getMaterial(name);
		if (material == null) {
			String alias = MATERIAL_ALIASES.get(name);
			if (alias != null) {
				material = Material.getMaterial(alias);
			}
		}
		if (material == null) {
			material = Material.getMaterial("LEGACY_" + name);
		}
		if (material == null) {
			throw new IllegalArgumentException("Unknown material: " + name);
		}
		return material;
	}

	private static byte[] copyAndRelease(ByteBuf byteBuf) {
		try {
			byte[] bytes = new byte[byteBuf.readableBytes()];
			byteBuf.getBytes(byteBuf.readerIndex(), bytes);
			return bytes;
		} finally {
			byteBuf.release();
		}
	}
}

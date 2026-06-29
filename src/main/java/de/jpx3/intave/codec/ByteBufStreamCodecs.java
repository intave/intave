package de.jpx3.intave.codec;

import de.jpx3.intave.codec.smart.ReflectionSmartCodecBuilder;
import de.jpx3.intave.codec.smart.SmartCodecBuilder;
import de.jpx3.intave.codec.smart.SmartCodecDecodeResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class ByteBufStreamCodecs {
  public static final StreamCodec<ByteBuf, ByteBuf, Boolean> BOOLEAN = StreamCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
  public static final StreamCodec<ByteBuf, ByteBuf, Integer> UNSIGNED_BYTE = StreamCodec.of(
    ByteBuf::writeByte,
    buf -> (int) buf.readUnsignedByte()
  );
  public static final StreamCodec<ByteBuf, ByteBuf, Integer> INTEGER = StreamCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
  public static final StreamCodec<ByteBuf, ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
  public static final StreamCodec<ByteBuf, ByteBuf, Float> FLOAT = StreamCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
  public static final StreamCodec<ByteBuf, ByteBuf, Double> DOUBLE = StreamCodec.of(ByteBuf::writeDouble, ByteBuf::readDouble);
  public static final StreamCodec<ByteBuf, ByteBuf, byte[]> BYTE_ARRAY = StreamCodec.of(
    (buf, arr) -> {
      INTEGER.encode(buf, arr.length);
      buf.writeBytes(arr);
    },
    buf -> {
      int length = INTEGER.decode(buf);
      byte[] arr = new byte[length];
      buf.readBytes(arr);
      return arr;
    }
  );
  public static final StreamCodec<ByteBuf, ByteBuf, String> STRING = BYTE_ARRAY.beforeAndAfter(String::new, String::getBytes);
  public static final StreamCodec<ByteBuf, ByteBuf, Material> MATERIAL = STRING.beforeAndAfter(ByteBufStreamCodecs::findOrThrow, Material::name);
  public static final StreamCodec<ByteBuf, ByteBuf, UUID> UUID = StreamCodec.of(
    (buf, uuid) -> {
      buf.writeLong(uuid.getMostSignificantBits());
      buf.writeLong(uuid.getLeastSignificantBits());
    },
    buf -> new UUID(buf.readLong(), buf.readLong())
  );

  public static <T> StreamCodec<ByteBuf, ByteBuf, List<T>> listCodecOf(
    StreamCodec<ByteBuf, ByteBuf, T> elementCodec
  ) {
    return elementCodec.toListCodec(ByteBufStreamCodecs.INTEGER);
  }

  public static <T> StreamCodec<ByteBuf, ByteBuf, List<T>> listCodecOf(
    StreamCodec<ByteBuf, ByteBuf, T> elementCodec,
    Function<Integer, List<T>> listFactory
  ) {
    return elementCodec.toListCodec(ByteBufStreamCodecs.INTEGER, listFactory);
  }

  public static <K, V> StreamCodec<ByteBuf, ByteBuf, Map<K, V>> mapCodec(
    StreamCodec<ByteBuf, ByteBuf, K> keyCodec,
    StreamCodec<ByteBuf, ByteBuf, V> valueCodec
  ) {
    return StreamCodec.mapCodec(keyCodec, valueCodec, ByteBufStreamCodecs.INTEGER);
  }

  public static <T> SmartCodecBuilder<ByteBuf, ByteBuf, T> smartCodec() {
    return StreamCodec.smartCodec(
      INTEGER,
      STRING,
      BYTE_ARRAY,
      Unpooled::buffer,
      ByteBufStreamCodecs::copyAndRelease,
      Unpooled::wrappedBuffer,
	    ReferenceCounted::release
    );
  }

  public static <T> StreamCodec<ByteBuf, ByteBuf, T> smartCodec(
    Function<SmartCodecBuilder<ByteBuf, ByteBuf, T>, SmartCodecBuilder<ByteBuf, ByteBuf, T>> schema,
    Function<SmartCodecDecodeResult, T> constructor
  ) {
    return schema.apply(smartCodec()).build(constructor);
  }

  public static <T> ReflectionSmartCodecBuilder<ByteBuf, ByteBuf, T> smartReflectionCodecBuilder(
    Class<T> type
  ) {
    return ByteBufStreamCodecs.<T>smartCodec().reflectionBuilderOn(type);
  }

  private final static Map<String, String> MATERIAL_ALIASES = new HashMap<>();

  static {
    MATERIAL_ALIASES.put("STATIONARY_WATER", "WATER");
    MATERIAL_ALIASES.put("STATIONARY_LAVA", "LAVA");
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
      throw new IllegalArgumentException("Unknown material: " + name);
    }
    return material;
  }

  private static byte[] copyAndRelease(
    ByteBuf byteBuf
  ) {
    try {
      byte[] bytes = new byte[byteBuf.readableBytes()];
      byteBuf.getBytes(byteBuf.readerIndex(), bytes);
      return bytes;
    } finally {
      byteBuf.release();
    }
  }
}

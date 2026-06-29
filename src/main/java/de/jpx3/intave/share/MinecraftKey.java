package de.jpx3.intave.share;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.klass.locate.Locate;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class MinecraftKey {
  public static final StreamCodec<ByteBuf, ByteBuf, MinecraftKey> STREAM_CODEC = StreamCodec.compound(
    ByteBufStreamCodecs.STRING, MinecraftKey::namespace,
    ByteBufStreamCodecs.STRING, MinecraftKey::path,
    MinecraftKey::new
  );

  private final String namespace;
  private final String path;

  public MinecraftKey(String namespace, String path) {
    this.namespace = namespace;
    this.path = path;
  }

  public String namespace() {
    return namespace;
  }

  public String path() {
    return path;
  }

	public String fullKey() {
		return namespace + ":" + path;
	}

  public static @Nullable MinecraftKey fromProtocolLib(
    @Nullable com.comphenix.protocol.wrappers.MinecraftKey protocolLibKey
  ) {
    if (protocolLibKey == null) {
      return null;
    }
    return new MinecraftKey(
      protocolLibKey.getPrefix(),
      protocolLibKey.getKey()
    );
  }

  public static MinecraftKey withDefaultNamespace(String path) {
    return new MinecraftKey("minecraft", path);
  }

  public static MinecraftKey from(String fullKey) {
    String[] parts = fullKey.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid MinecraftKey: " + fullKey);
    }
    return new MinecraftKey(parts[0], parts[1]);
  }

  private static final class ConstructorHolder {
    static final Constructor<?> NATIVE_RESOURCE_LOCATION_CONSTRUCTOR;
    static {
      Class<?> minecraftKeyClass = Locate.classByKey("MinecraftKey");
      try {
        NATIVE_RESOURCE_LOCATION_CONSTRUCTOR = minecraftKeyClass.getDeclaredConstructor(String.class, String.class);
        NATIVE_RESOURCE_LOCATION_CONSTRUCTOR.setAccessible(true);
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }
  }

  public Object toNativeResourceLocation() {
    try {
      return ConstructorHolder.NATIVE_RESOURCE_LOCATION_CONSTRUCTOR.newInstance(namespace, path);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}

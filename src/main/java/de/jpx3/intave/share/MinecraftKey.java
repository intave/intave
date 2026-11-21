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

package de.jpx3.intave.share;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class MinecraftKey {
  public static final StreamCodec<ByteBuf, ByteBuf, MinecraftKey> STREAM_CODEC = ByteBufStreamCodecs.VARINT_STRING.beforeAndAfter(
    MinecraftKey::parse, MinecraftKey::fullKey
  );
  public static final StreamCodec<ByteBuf, ByteBuf, MinecraftKey> INTERNAL_STREAM_CODEC = StreamCodec.compound(
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

  @Override
  public String toString() {
    return fullKey();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof MinecraftKey)) return false;
    MinecraftKey other = (MinecraftKey) obj;
    return namespace.equals(other.namespace) && path.equals(other.path);
  }

  @Override
  public int hashCode() {
    return 31 * namespace.hashCode() + path.hashCode();
  }

  @Deprecated
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

  public static MinecraftKey parse(String value) {
    return value.indexOf(':') < 0 ? withDefaultNamespace(value) : from(value);
  }

  public static MinecraftKey from(String fullKey) {
    String[] parts = fullKey.split(":", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid MinecraftKey: " + fullKey);
    }
    return new MinecraftKey(parts[0], parts[1]);
  }
}

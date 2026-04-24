package de.jpx3.intave.minecraft;

import io.netty.buffer.ByteBuf;

import java.lang.reflect.Constructor;
import java.util.function.Function;

public final class MinecraftMethods {
  private static Function<ByteBuf, Object> friendlyBufConstructor;

  private MinecraftMethods() {}

  public static Function<ByteBuf, Object> getFriendlyBufBufConstructor() {
    if (friendlyBufConstructor == null) {
      friendlyBufConstructor = resolveFriendlyBufConstructor();
    }
    return friendlyBufConstructor;
  }

  private static Function<ByteBuf, Object> resolveFriendlyBufConstructor() {
    Class<?> friendlyBuf = MinecraftReflection.getPacketDataSerializerClass();
    try {
      Constructor<?> constructor = friendlyBuf.getDeclaredConstructor(ByteBuf.class);
      constructor.setAccessible(true);
      return byteBuf -> {
        try {
          return constructor.newInstance(byteBuf);
        } catch (ReflectiveOperationException exception) {
          throw new IllegalStateException("Cannot construct " + friendlyBuf.getName(), exception);
        }
      };
    } catch (Throwable ignored) {
      return byteBuf -> byteBuf;
    }
  }
}

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

import de.jpx3.intave.cleanup.ReferenceMap;
import io.netty.buffer.ByteBuf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CodecTranslator {
  private final static Map<String, StreamCodec<?, ?, ?>> translatedCodecs = ReferenceMap.soft(new ConcurrentHashMap<>());
  private final static Map<String, Object> streamCodecs = ReferenceMap.soft(new ConcurrentHashMap<>());

  public static <T> StreamCodec<ByteBuf, ByteBuf, T> translatedCodecOf(Class<? extends T> clazz) {
	  //noinspection unchecked
	  return (StreamCodec<ByteBuf, ByteBuf, T>)
      translatedCodecs.computeIfAbsent(clazz.getName(), clazzName -> translateCodecOf(clazz));
  }

  private static StreamCodec<?, ?, ?> translateCodecOf(Class<?> clazz) {
    Object rawStreamCodec = rawStreamCodecOf(clazz);
    if (rawStreamCodec instanceof StreamCodec<?, ?, ?>) {
      return (StreamCodec<?, ?, ?>) rawStreamCodec;
    }
    Class<?> aClass = rawStreamCodec.getClass();
    Method encodeMethod, decodeMethod;
    try {
      encodeMethod = aClass.getMethod("encode", Object.class, Object.class);
      decodeMethod = aClass.getMethod("decode", Object.class);
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot find encode method", exception);
    }
    encodeMethod.setAccessible(true);
    decodeMethod.setAccessible(true);
    return StreamCodec.of((output, object) -> {
      try {
        encodeMethod.invoke(rawStreamCodec, output, object);
      } catch (Throwable throwable) {
        throw new IllegalStateException("Cannot encode object", throwable);
      }
    }, (object) -> {
      try {
        return decodeMethod.invoke(rawStreamCodec, object);
      } catch (Throwable throwable) {
        throw new IllegalStateException("Cannot decode object", throwable);
      }
    });
  }

  private static Object rawStreamCodecOf(Class<?> clazz) {
    return streamCodecs.computeIfAbsent(clazz.getName(), clazzName -> findRawStreamCodecIn(clazz));
  }

  private static Object findRawStreamCodecIn(Class<?> clazz) {
    for (Field declaredField : clazz.getDeclaredFields()) {
      if (declaredField.getType().getName().endsWith("StreamCodec") || declaredField.getType().isAssignableFrom(StreamCodec.class)) {
        try {
          declaredField.setAccessible(true);
          return declaredField.get(null);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Cannot access codec field", e);
        }
      }
    }
    throw new IllegalStateException("No codec found for " + clazz);
  }
}

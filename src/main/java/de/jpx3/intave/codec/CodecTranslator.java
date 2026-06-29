package de.jpx3.intave.codec;

import de.jpx3.intave.cleanup.ReferenceMap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CodecTranslator {
  private final static Map<String, StreamCodec<?, ?, ?>> translatedCodecs = ReferenceMap.soft(new ConcurrentHashMap<>());
  private final static Map<String, Object> streamCodecs = ReferenceMap.soft(new ConcurrentHashMap<>());

  public static StreamCodec<?, ?, ?> translatedCodecOf(Class<?> clazz) {
    return translatedCodecs.computeIfAbsent(clazz.getName(), clazzName -> translateCodecOf(clazz));
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
      if (declaredField.getType().getName().endsWith("StreamCodec")) {
        try {
          return declaredField.get(null);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Cannot access codec field", e);
        }
      }
    }
    throw new IllegalStateException("No codec found for " + clazz);
  }
}

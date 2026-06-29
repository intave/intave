package de.jpx3.intave.codec.dispatch;

import de.jpx3.intave.codec.StreamCodec;

import java.util.*;
import java.util.function.Supplier;

public final class DispatchCodecBuilder<I, O, K, T> {
  private final StreamCodec<I, O, K> keyCodec;
  private final List<DispatchTarget<I, O, K, T>> targets = new ArrayList<>();
  private final Map<K, DispatchTarget<I, O, K, T>> targetsByKey = new LinkedHashMap<>();

  public DispatchCodecBuilder(StreamCodec<I, O, K> keyCodec) {
    this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
  }

  public <S extends T> DispatchCodecBuilder<I, O, K, T> subtype(
    K key, Class<S> type, StreamCodec<I, O, S> codec
  ) {
    return subtype(key, type, () -> codec);
  }

  public <S extends T> DispatchCodecBuilder<I, O, K, T> subtype(
    K key, Class<S> type,
    Supplier<? extends StreamCodec<I, O, S>> codecSupplier
  ) {
    if (targetsByKey.containsKey(key)) {
      throw new IllegalArgumentException("Duplicate dispatch key: " + key);
    }
    for (DispatchTarget<I, O, K, T> variant : targets) {
      if (variant.type().equals(type)) {
        throw new IllegalArgumentException("Duplicate dispatch type: " + type.getName());
      }
    }
    DispatchTarget<I, O, K, T> variant = new DispatchTarget<>(key, type, codecSupplier);
    targets.add(variant);
    targetsByKey.put(key, variant);
    return this;
  }

  public StreamCodec<I, O, T> build() {
    List<DispatchTarget<I, O, K, T>> encodeVariants = new ArrayList<>(targets);
    Map<K, DispatchTarget<I, O, K, T>> decodeVariants = new LinkedHashMap<>(targetsByKey);
    return new StreamCodec<I, O, T>() {
      @Override
      public void encode(O output, T value) {
        if (value == null) {
          throw new IllegalArgumentException("Cannot encode null dispatch value");
        }
        DispatchTarget<I, O, K, T> variant = variantOf(value, encodeVariants);
        keyCodec.encode(output, variant.key());
        variant.encode(output, value);
      }

      @Override
      public T decode(I input) {
        K key = keyCodec.decode(input);
        DispatchTarget<I, O, K, T> variant = decodeVariants.get(key);
        if (variant == null) {
          throw new IllegalStateException("No dispatch codec registered for key: " + key);
        }
        return variant.decode(input);
      }
    };
  }

  private DispatchTarget<I, O, K, T> variantOf(
    T value, List<DispatchTarget<I, O, K, T>> variants
  ) {
    Class<?> valueType = value.getClass();
    for (DispatchTarget<I, O, K, T> variant : variants) {
      if (variant.type().equals(valueType)) {
        return variant;
      }
    }
    for (DispatchTarget<I, O, K, T> variant : variants) {
      if (variant.matches(value)) {
        return variant;
      }
    }
    throw new IllegalStateException("No dispatch codec registered for type: " + valueType.getName());
  }
}

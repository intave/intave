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

import de.jpx3.intave.codec.dispatch.DispatchCodecBuilder;
import de.jpx3.intave.codec.smart.SmartCodecBuilder;
import de.jpx3.intave.codec.transform.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface StreamCodec<I, O, T> extends StreamEncoder<O, T>, StreamDecoder<I, T> {
  static <I, O, T> StreamCodec<I, O, T> of(T value) {
    return new StreamCodec<I, O, T>() {
      @Override
      public void encode(O o, T t) {
        // No-op
      }

      @Override
      public T decode(I input) {
        return value;
      }
    };
  }

  static <I, O, T> StreamCodec<I, O, T> of(StreamEncoder<O, T> encoder, StreamDecoder<I, T> decoder) {
    return new StreamCodec<I, O, T>() {
      @Override
      public void encode(O o, T t) {
        encoder.encode(o, t);
      }

      @Override
      public T decode(I i) {
        return decoder.decode(i);
      }
    };
  }

  static <I, O, T> StreamCodec<I, O, T> ofMember(StreamMemberEncoder<O, T> encoder, StreamDecoder<I, T> decoder) {
    return new StreamCodec<I, O, T>() {
      @Override
      public void encode(O o, T t) {
        encoder.encode(t, o);
      }

      @Override
      public T decode(I i) {
        return decoder.decode(i);
      }
    };
  }

  static <I, O, K, T> DispatchCodecBuilder<I, O, K, T> dispatchBuilder(
    // type class used to get our types correct, but it is not necessary for the runtime
    Class<T> typeClass, StreamCodec<I, O, K> keyCodec
  ) {
    return new DispatchCodecBuilder<>(keyCodec);
  }

  static <B, C, T1> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> valueOneConverter,
    Function<C, T1> valueOneGetter,
    Function<T1, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B b, C c) {
        valueOneConverter.encode(b, valueOneGetter.apply(c));
      }

      @Override
      public C decode(B b) {
        return constructor.apply(valueOneConverter.decode(b));
      }
    };
  }

  static <B, C, T1, T2> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> valueOneConverter,
    Function<C, T1> valueOneGetter,
    StreamCodec<B, B, T2> valueTwoConverter,
    Function<C, T2> valueTwoGetter,
    BiFunction<T1, T2, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B b, C c) {
        if (c == null) {
          throw new IllegalArgumentException("Cannot encode null value");
        }
        valueOneConverter.encode(b, valueOneGetter.apply(c));
        valueTwoConverter.encode(b, valueTwoGetter.apply(c));
      }

      @Override
      public C decode(B b) {
        return constructor.apply(
          valueOneConverter.decode(b),
          valueTwoConverter.decode(b)
        );
      }
    };
  }

  static <B, C, T1, T2, T3> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> valueOneConverter,
    Function<C, T1> valueOneGetter,
    StreamCodec<B, B, T2> valueTwoConverter,
    Function<C, T2> valueTwoGetter,
    StreamCodec<B, B, T3> valueThreeConverter,
    Function<C, T3> valueThreeGetter,
    TriFunction<T1, T2, T3, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B b, C c) {
        valueOneConverter.encode(b, valueOneGetter.apply(c));
        valueTwoConverter.encode(b, valueTwoGetter.apply(c));
        valueThreeConverter.encode(b, valueThreeGetter.apply(c));
      }

      @Override
      public C decode(B b) {
        T1 t1 = valueOneConverter.decode(b);
        T2 t2 = valueTwoConverter.decode(b);
        T3 t3 = valueThreeConverter.decode(b);
        return constructor.apply(t1, t2, t3);
      }
    };
  }

  static <B, C, T1, T2, T3, T4> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> valueOneConverter,
    Function<C, T1> valueOneGetter,
    StreamCodec<B, B, T2> valueTwoConverter,
    Function<C, T2> valueTwoGetter,
    StreamCodec<B, B, T3> valueThreeConverter,
    Function<C, T3> valueThreeGetter,
    StreamCodec<B, B, T4> valueFourConverter,
    Function<C, T4> valueFourGetter,
    QuadFunction<T1, T2, T3, T4, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B b, C c) {
        valueOneConverter.encode(b, valueOneGetter.apply(c));
        valueTwoConverter.encode(b, valueTwoGetter.apply(c));
        valueThreeConverter.encode(b, valueThreeGetter.apply(c));
        valueFourConverter.encode(b, valueFourGetter.apply(c));
      }

      @Override
      public C decode(B b) {
        T1 t1 = valueOneConverter.decode(b);
        T2 t2 = valueTwoConverter.decode(b);
        T3 t3 = valueThreeConverter.decode(b);
        T4 t4 = valueFourConverter.decode(b);
        return constructor.apply(t1, t2, t3, t4);
      }
    };
  }

  static <B, C, T1, T2, T3, T4, T5> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> codec1, Function<C, T1> getter1,
    StreamCodec<B, B, T2> codec2, Function<C, T2> getter2,
    StreamCodec<B, B, T3> codec3, Function<C, T3> getter3,
    StreamCodec<B, B, T4> codec4, Function<C, T4> getter4,
    StreamCodec<B, B, T5> codec5, Function<C, T5> getter5,
    PentaFunction<T1, T2, T3, T4, T5, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B buffer, C value) {
        codec1.encode(buffer, getter1.apply(value));
        codec2.encode(buffer, getter2.apply(value));
        codec3.encode(buffer, getter3.apply(value));
        codec4.encode(buffer, getter4.apply(value));
        codec5.encode(buffer, getter5.apply(value));
      }

      @Override
      public C decode(B buffer) {
        return constructor.apply(
          codec1.decode(buffer),
          codec2.decode(buffer),
          codec3.decode(buffer),
          codec4.decode(buffer),
          codec5.decode(buffer)
        );
      }
    };
  }

  static <B, C, T1, T2, T3, T4, T5, T6> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> codec1, Function<C, T1> getter1,
    StreamCodec<B, B, T2> codec2, Function<C, T2> getter2,
    StreamCodec<B, B, T3> codec3, Function<C, T3> getter3,
    StreamCodec<B, B, T4> codec4, Function<C, T4> getter4,
    StreamCodec<B, B, T5> codec5, Function<C, T5> getter5,
    StreamCodec<B, B, T6> codec6, Function<C, T6> getter6,
    HexaFunction<T1, T2, T3, T4, T5, T6, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B buffer, C value) {
        codec1.encode(buffer, getter1.apply(value));
        codec2.encode(buffer, getter2.apply(value));
        codec3.encode(buffer, getter3.apply(value));
        codec4.encode(buffer, getter4.apply(value));
        codec5.encode(buffer, getter5.apply(value));
        codec6.encode(buffer, getter6.apply(value));
      }

      @Override
      public C decode(B buffer) {
        return constructor.apply(
          codec1.decode(buffer),
          codec2.decode(buffer),
          codec3.decode(buffer),
          codec4.decode(buffer),
          codec5.decode(buffer),
          codec6.decode(buffer)
        );
      }
    };
  }

  static <B, C, T1, T2, T3, T4, T5, T6, T7> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> codec1, Function<C, T1> getter1,
    StreamCodec<B, B, T2> codec2, Function<C, T2> getter2,
    StreamCodec<B, B, T3> codec3, Function<C, T3> getter3,
    StreamCodec<B, B, T4> codec4, Function<C, T4> getter4,
    StreamCodec<B, B, T5> codec5, Function<C, T5> getter5,
    StreamCodec<B, B, T6> codec6, Function<C, T6> getter6,
    StreamCodec<B, B, T7> codec7, Function<C, T7> getter7,
    HeptaFunction<T1, T2, T3, T4, T5, T6, T7, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B buffer, C value) {
        codec1.encode(buffer, getter1.apply(value));
        codec2.encode(buffer, getter2.apply(value));
        codec3.encode(buffer, getter3.apply(value));
        codec4.encode(buffer, getter4.apply(value));
        codec5.encode(buffer, getter5.apply(value));
        codec6.encode(buffer, getter6.apply(value));
        codec7.encode(buffer, getter7.apply(value));
      }

      @Override
      public C decode(B buffer) {
        return constructor.apply(
          codec1.decode(buffer),
          codec2.decode(buffer),
          codec3.decode(buffer),
          codec4.decode(buffer),
          codec5.decode(buffer),
          codec6.decode(buffer),
          codec7.decode(buffer)
        );
      }
    };
  }

  static <B, C, T1, T2, T3, T4, T5, T6, T7, T8> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> codec1, Function<C, T1> getter1,
    StreamCodec<B, B, T2> codec2, Function<C, T2> getter2,
    StreamCodec<B, B, T3> codec3, Function<C, T3> getter3,
    StreamCodec<B, B, T4> codec4, Function<C, T4> getter4,
    StreamCodec<B, B, T5> codec5, Function<C, T5> getter5,
    StreamCodec<B, B, T6> codec6, Function<C, T6> getter6,
    StreamCodec<B, B, T7> codec7, Function<C, T7> getter7,
    StreamCodec<B, B, T8> codec8, Function<C, T8> getter8,
    OctaFunction<T1, T2, T3, T4, T5, T6, T7, T8, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B buffer, C value) {
        codec1.encode(buffer, getter1.apply(value));
        codec2.encode(buffer, getter2.apply(value));
        codec3.encode(buffer, getter3.apply(value));
        codec4.encode(buffer, getter4.apply(value));
        codec5.encode(buffer, getter5.apply(value));
        codec6.encode(buffer, getter6.apply(value));
        codec7.encode(buffer, getter7.apply(value));
        codec8.encode(buffer, getter8.apply(value));
      }

      @Override
      public C decode(B buffer) {
        return constructor.apply(
          codec1.decode(buffer),
          codec2.decode(buffer),
          codec3.decode(buffer),
          codec4.decode(buffer),
          codec5.decode(buffer),
          codec6.decode(buffer),
          codec7.decode(buffer),
          codec8.decode(buffer)
        );
      }
    };
  }

  static <B, C, T1, T2, T3, T4, T5, T6, T7, T8, T9> StreamCodec<B, B, C> compound(
    StreamCodec<B, B, T1> codec1, Function<C, T1> getter1,
    StreamCodec<B, B, T2> codec2, Function<C, T2> getter2,
    StreamCodec<B, B, T3> codec3, Function<C, T3> getter3,
    StreamCodec<B, B, T4> codec4, Function<C, T4> getter4,
    StreamCodec<B, B, T5> codec5, Function<C, T5> getter5,
    StreamCodec<B, B, T6> codec6, Function<C, T6> getter6,
    StreamCodec<B, B, T7> codec7, Function<C, T7> getter7,
    StreamCodec<B, B, T8> codec8, Function<C, T8> getter8,
    StreamCodec<B, B, T9> codec9, Function<C, T9> getter9,
    NonaFunction<T1, T2, T3, T4, T5, T6, T7, T8, T9, C> constructor
  ) {
    return new StreamCodec<B, B, C>() {
      @Override
      public void encode(B buffer, C value) {
        codec1.encode(buffer, getter1.apply(value));
        codec2.encode(buffer, getter2.apply(value));
        codec3.encode(buffer, getter3.apply(value));
        codec4.encode(buffer, getter4.apply(value));
        codec5.encode(buffer, getter5.apply(value));
        codec6.encode(buffer, getter6.apply(value));
        codec7.encode(buffer, getter7.apply(value));
        codec8.encode(buffer, getter8.apply(value));
        codec9.encode(buffer, getter9.apply(value));
      }

      @Override
      public C decode(B buffer) {
        return constructor.apply(
          codec1.decode(buffer),
          codec2.decode(buffer),
          codec3.decode(buffer),
          codec4.decode(buffer),
          codec5.decode(buffer),
          codec6.decode(buffer),
          codec7.decode(buffer),
          codec8.decode(buffer),
          codec9.decode(buffer)
        );
      }
    };
  }

  // map

	static <I, O, K, V> StreamCodec<I, O, Map<K, V>> mapCodec(
		StreamCodec<I, O, K> keyCodec,
		StreamCodec<I, O, V> valueCodec,
		StreamCodec<I, O, Integer> lengthCodec
	) {
		return mapCodec(keyCodec, valueCodec, lengthCodec, HashMap::new);
	}

  static <I, O, K, V> StreamCodec<I, O, Map<K, V>> mapCodec(
    StreamCodec<I, O, K> keyCodec,
    StreamCodec<I, O, V> valueCodec,
    StreamCodec<I, O, Integer> lengthCodec,
    Supplier<Map<K, V>> mapConstructor
  ) {
    return StreamCodec.of(
      (o, kvMap) -> {
        lengthCodec.encode(o, kvMap.size());
        for (Map.Entry<K, V> entry : kvMap.entrySet()) {
          K key = entry.getKey();
          V value = entry.getValue();
          keyCodec.encode(o, key);
          valueCodec.encode(o, value);
        }
      },
      i -> {
        int length = lengthCodec.decode(i);
        if (length < 0) {
          throw new IllegalStateException("Negative map length: " + length);
        }
        if (length > 8192) {
          throw new IllegalStateException("Map length exceeds maximum of 8192: " + length);
        }
        Map<K, V> map = mapConstructor.get();
        for (int j = 0; j < length; j++) {
          K key = keyCodec.decode(i);
          V value = valueCodec.decode(i);
          map.put(key, value);
        }
        return map;
      }
    );
  }

  static <I, O, T> SmartCodecBuilder<I, O, T> smartCodec(
    StreamCodec<I, O, Integer> lengthCodec,
    StreamCodec<I, O, String> nameCodec,
    StreamCodec<I, O, byte[]> payloadCodec,
    Supplier<O> payloadOutputFactory,
    Function<O, byte[]> payloadExtractor,
    Function<byte[], I> payloadInputFactory,
    Consumer<I> payloadInputCleaner
  ) {
    return new SmartCodecBuilder<>(
      lengthCodec,
      nameCodec,
      payloadCodec,
      payloadOutputFactory,
      payloadExtractor,
      payloadInputFactory,
      payloadInputCleaner
    );
  }

  default <F> StreamCodec<I ,O, F> beforeAndAfter(
    Function<T, F> wrapper,
    Function<F, T> unwrapper
  ) {
    return new StreamCodec<I, O, F>() {
      @Override
      public void encode(O o, F f) {
        StreamCodec.this.encode(o, unwrapper.apply(f));
      }

      @Override
      public F decode(I i) {
        return wrapper.apply(StreamCodec.this.decode(i));
      }
    };
  }

	default StreamCodec<I, O, List<T>> toListCodec(
		StreamCodec<I, O, Integer> sizeCodec
	) {
		return toListCodec(sizeCodec, ArrayList::new);
	}

	default StreamCodec<I, O, List<T>> toListCodec(
    StreamCodec<I, O, Integer> sizeCodec,
    Function<Integer, List<T>> listFactory
  ) {
    return new StreamCodec<I, O, List<T>>() {
      @Override
      public void encode(O o, List<T> ts) {
        sizeCodec.encode(o, ts.size());
        for (T t : ts) {
          StreamCodec.this.encode(o, t);
        }
      }

      @Override
      public List<T> decode(I i) {
        int size = sizeCodec.decode(i);
        if (size < 0 || size > 1048576) {
          throw new IllegalStateException("Invalid list size: " + size);
        }
        List<T> list = listFactory.apply(size);
        for (int j = 0; j < size; j++) {
          list.add(StreamCodec.this.decode(i));
        }
        return list;
      }
    };
  }

  default StreamCodec<I, O, T> nullable(
    StreamCodec<I, O, Boolean> nullabilityCodec
  ) {
    return new StreamCodec<I, O, T>() {
      @Override
      public void encode(O o, T t) {
        if (t == null) {
          nullabilityCodec.encode(o, true);
        } else {
          nullabilityCodec.encode(o, false);
          StreamCodec.this.encode(o, t);
        }
      }

      @Override
      public T decode(I i) {
        boolean isNull = nullabilityCodec.decode(i);
        if (isNull) {
          return null;
        } else {
          return StreamCodec.this.decode(i);
        }
      }
    };
  }
}

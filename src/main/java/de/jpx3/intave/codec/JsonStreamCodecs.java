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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.transform.QuadFunction;
import de.jpx3.intave.codec.transform.TriFunction;
import org.bukkit.Material;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class JsonStreamCodecs {
  public static final StreamCodec<JsonReader, JsonWriter, Double> DOUBLE = of(
    JsonStreamCodecs::writeDouble,
    JsonStreamCodecs::readDouble
  );
  public static final StreamCodec<JsonReader, JsonWriter, Float> FLOAT = of(
    JsonStreamCodecs::writeFloat,
    JsonStreamCodecs::readFloat
  );
  public static final StreamCodec<JsonReader, JsonWriter, Integer> INTEGER = of(
    JsonWriter::value,
    JsonReader::nextInt
  );
  public static final StreamCodec<JsonReader, JsonWriter, Long> LONG = of(
    JsonWriter::value,
    JsonReader::nextLong
  );
  public static final StreamCodec<JsonReader, JsonWriter, Boolean> BOOLEAN = of(
    JsonWriter::value,
    JsonReader::nextBoolean
  );
  public static final StreamCodec<JsonReader, JsonWriter, String> STRING = of(
    JsonWriter::value,
    reader -> {
      if (reader.peek() == JsonToken.NULL) {
        reader.nextNull();
        return null;
      }
      return reader.nextString();
    }
  );
  public static final StreamCodec<JsonReader, JsonWriter, Comparable<?>> JSON_PRIMITIVE = of(
    JsonStreamCodecs::writePrimitive,
    JsonStreamCodecs::readPrimitive
  );
  public static final StreamCodec<JsonReader, JsonWriter, Material> MATERIAL = STRING.beforeAndAfter(
	  Material::getMaterial, Enum::name
  );

  private JsonStreamCodecs() {
  }

  public static <T> JsonField<T, Double> doubleField(String name, Function<T, Double> getter) {
    return field(name, DOUBLE, getter, 0.0);
  }

  public static <T> JsonField<T, Float> floatField(String name, Function<T, Float> getter) {
    return field(name, FLOAT, getter, 0.0F);
  }

  public static <T> JsonField<T, Integer> integerField(String name, Function<T, Integer> getter) {
    return field(name, INTEGER, getter, 0);
  }

  public static <T> JsonField<T, Long> longField(String name, Function<T, Long> getter) {
    return field(name, LONG, getter, 0L);
  }

  public static <T> JsonField<T, Boolean> booleanField(String name, Function<T, Boolean> getter) {
    return field(name, BOOLEAN, getter, false);
  }

  public static <T> JsonField<T, Boolean> booleanField(
    String name,
    Function<T, Boolean> getter,
    boolean defaultValue
  ) {
    return field(name, BOOLEAN, getter, defaultValue);
  }

  public static <T> JsonField<T, String> stringField(String name, Function<T, String> getter) {
    return field(name, STRING, getter, null);
  }

  public static <T> JsonField<T, UUID> uuidField(String name, Function<T, UUID> getter, UUID defaultValue) {
    return field(name, STRING.beforeAndAfter(UUID::fromString, UUID::toString), getter, defaultValue);
  }

  public static <T, V> JsonField<T, V> field(
    String name,
    StreamCodec<JsonReader, JsonWriter, V> codec,
    Function<T, V> getter,
    V defaultValue
  ) {
    return new JsonField<>(name, codec, getter, defaultValue);
  }

  /**
   * Creates an object codec without imposing an arity limit on its constructor.
   * This is preferable for wire-format objects whose schema grows over time.
   */
  @SafeVarargs
  public static <T> JsonObjectCodec<T> object(
    Function<JsonValues, T> constructor,
    JsonField<T, ?>... fields
  ) {
    List<JsonField<T, ?>> fieldList = Arrays.asList(fields);
    return objectCodec(fieldList, values -> constructor.apply(new JsonValues(values)));
  }

  public static <T> StreamCodec<JsonReader, JsonWriter, T> nullable(
    StreamCodec<JsonReader, JsonWriter, T> codec
  ) {
    Objects.requireNonNull(codec, "codec");
    return of(
      (writer, value) -> writeNullable(writer, codec, value),
      reader -> readNullable(reader, codec)
    );
  }

  public static <T, A, B> JsonObjectCodec<T> object(
    JsonField<T, A> first,
    JsonField<T, B> second,
    BiFunction<A, B, T> constructor
  ) {
    return objectCodec(
      Arrays.asList(first, second),
      values -> constructor.apply(value(values, 0), value(values, 1))
    );
  }

  public static <T, A, B, C> JsonObjectCodec<T> object(
    JsonField<T, A> first,
    JsonField<T, B> second,
    JsonField<T, C> third,
    TriFunction<A, B, C, T> constructor
  ) {
    return objectCodec(
      Arrays.asList(first, second, third),
      values -> constructor.apply(value(values, 0), value(values, 1), value(values, 2))
    );
  }

  public static <T, A, B, C, D> JsonObjectCodec<T> object(
    JsonField<T, A> first,
    JsonField<T, B> second,
    JsonField<T, C> third,
    JsonField<T, D> fourth,
    QuadFunction<A, B, C, D, T> constructor
  ) {
    return objectCodec(
      Arrays.asList(first, second, third, fourth),
      values -> constructor.apply(
        value(values, 0), value(values, 1), value(values, 2), value(values, 3)
      )
    );
  }

  public static <T, A, B, C, D, E, F, G> JsonObjectCodec<T> object(
    JsonField<T, A> first,
    JsonField<T, B> second,
    JsonField<T, C> third,
    JsonField<T, D> fourth,
    JsonField<T, E> fifth,
    JsonField<T, F> sixth,
    JsonField<T, G> seventh,
    HeptaFunction<A, B, C, D, E, F, G, T> constructor
  ) {
    return objectCodec(
      Arrays.asList(first, second, third, fourth, fifth, sixth, seventh),
      values -> constructor.apply(
        value(values, 0), value(values, 1), value(values, 2), value(values, 3),
        value(values, 4), value(values, 5), value(values, 6)
      )
    );
  }

  public static <T, A, B, C, D, E, F, G, H> JsonObjectCodec<T> object(
    JsonField<T, A> first,
    JsonField<T, B> second,
    JsonField<T, C> third,
    JsonField<T, D> fourth,
    JsonField<T, E> fifth,
    JsonField<T, F> sixth,
    JsonField<T, G> seventh,
    JsonField<T, H> eighth,
    OctaFunction<A, B, C, D, E, F, G, H, T> constructor
  ) {
    return objectCodec(
      Arrays.asList(first, second, third, fourth, fifth, sixth, seventh, eighth),
      values -> constructor.apply(
        value(values, 0), value(values, 1), value(values, 2), value(values, 3),
        value(values, 4), value(values, 5), value(values, 6), value(values, 7)
      )
    );
  }

  public static <T, A, B, C, D, E, F, G, H, I> JsonObjectCodec<T> object(
    JsonField<T, A> first,
    JsonField<T, B> second,
    JsonField<T, C> third,
    JsonField<T, D> fourth,
    JsonField<T, E> fifth,
    JsonField<T, F> sixth,
    JsonField<T, G> seventh,
    JsonField<T, H> eighth,
    JsonField<T, I> ninth,
    NonaFunction<A, B, C, D, E, F, G, H, I, T> constructor
  ) {
    return objectCodec(
      Arrays.asList(first, second, third, fourth, fifth, sixth, seventh, eighth, ninth),
      values -> constructor.apply(
        value(values, 0), value(values, 1), value(values, 2), value(values, 3),
        value(values, 4), value(values, 5), value(values, 6), value(values, 7),
        value(values, 8)
      )
    );
  }

  private static <T> JsonObjectCodec<T> objectCodec(
    List<? extends JsonField<T, ?>> fields,
    Function<Object[], T> constructor
  ) {
    return new JsonObjectCodec<>(fields, Collections.emptyList(), constructor);
  }

  @SuppressWarnings("unchecked")
  private static <V> V value(Object[] values, int index) {
    return (V) values[index];
  }

  public static <T> StreamCodec<JsonReader, JsonWriter, T> of(
    JsonEncoder<T> encoder,
    JsonDecoder<T> decoder
  ) {
    return StreamCodec.of(
      (writer, value) -> {
        try {
          encoder.encode(writer, value);
        } catch (IOException exception) {
          throw new UncheckedIOException(exception);
        }
      },
      reader -> {
        try {
          return decoder.decode(reader);
        } catch (IOException exception) {
          throw new UncheckedIOException(exception);
        }
      }
    );
  }

  public static <T> StreamCodec<JsonReader, JsonWriter, T> lazy(
    Supplier<? extends StreamCodec<JsonReader, JsonWriter, T>> codecSupplier
  ) {
    Objects.requireNonNull(codecSupplier, "codecSupplier");
    return new StreamCodec<JsonReader, JsonWriter, T>() {
      private volatile StreamCodec<JsonReader, JsonWriter, T> delegate;

      @Override
      public void encode(JsonWriter writer, T value) {
        delegate().encode(writer, value);
      }

      @Override
      public T decode(JsonReader reader) {
        return delegate().decode(reader);
      }

      private StreamCodec<JsonReader, JsonWriter, T> delegate() {
        StreamCodec<JsonReader, JsonWriter, T> codec = delegate;
        if (codec == null) {
          synchronized (this) {
            codec = delegate;
            if (codec == null) {
              codec = Objects.requireNonNull(codecSupplier.get(), "codecSupplier returned null");
              delegate = codec;
            }
          }
        }
        return codec;
      }
    };
  }

  public static <E extends Enum<E>> StreamCodec<JsonReader, JsonWriter, E> enumCodec(
    Class<E> enumClass
  ) {
    Objects.requireNonNull(enumClass, "enumClass");
    return of(
      (writer, value) -> {
        if (value == null) {
          writer.nullValue();
        } else {
          writer.value(value.name());
        }
      },
      reader -> {
        if (reader.peek() == JsonToken.NULL) {
          reader.nextNull();
          return null;
        }
        return Enum.valueOf(enumClass, reader.nextString());
      }
    );
  }

  /** Wraps positional fields in a JSON array. The decoder must consume exactly those fields. */
  public static <T> StreamCodec<JsonReader, JsonWriter, T> tuple(JsonEncoder<T> encoder, JsonDecoder<T> decoder) {
    return of((writer, value) -> {
      writer.beginArray();
      encoder.encode(writer, value);
      writer.endArray();
    }, reader -> {
      reader.beginArray();
      T value = decoder.decode(reader);
      reader.endArray();
      return value;
    });
  }

  public static <T> StreamCodec<JsonReader, JsonWriter, List<T>> listCodecOf(
    StreamCodec<JsonReader, JsonWriter, T> elementCodec
  ) {
    Objects.requireNonNull(elementCodec, "elementCodec");
    return of(
      (writer, values) -> {
        writer.beginArray();
        for (T value : values) {
          elementCodec.encode(writer, value);
        }
        writer.endArray();
      },
      reader -> {
        List<T> values = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
          if (values.size() >= 1048576) {
            throw new IllegalStateException("JSON list exceeds maximum size of 1048576");
          }
          values.add(elementCodec.decode(reader));
        }
        reader.endArray();
        return values;
      }
    );
  }

  public static <V> StreamCodec<JsonReader, JsonWriter, Map<String, V>> stringMapCodec(
    StreamCodec<JsonReader, JsonWriter, V> valueCodec
  ) {
    Objects.requireNonNull(valueCodec, "valueCodec");
    return of(
      (writer, values) -> {
        writer.beginObject();
        Map<String, V> ordered = values instanceof SortedMap ? values : new TreeMap<>(values);
        for (Map.Entry<String, V> entry : ordered.entrySet()) {
          writer.name(entry.getKey());
          valueCodec.encode(writer, entry.getValue());
        }
        writer.endObject();
      },
      reader -> {
        Map<String, V> values = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
          if (values.size() >= 8192) {
            throw new IllegalStateException("JSON map exceeds maximum size of 8192");
          }
          values.put(reader.nextName(), valueCodec.decode(reader));
        }
        reader.endObject();
        return values;
      }
    );
  }

  public static <T> String encodeToString(
    StreamCodec<JsonReader, JsonWriter, T> codec,
    T value
  ) {
    StringWriter output = new StringWriter();
    try (JsonWriter writer = new JsonWriter(output)) {
      writer.setSerializeNulls(true);
      codec.encode(writer, value);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return output.toString();
  }

  public static <T> JsonElement encodeToTree(
    StreamCodec<JsonReader, JsonWriter, T> codec,
    T value
  ) {
    return new JsonParser().parse(encodeToString(codec, value));
  }

  public static <T> T decodeFromString(
    StreamCodec<JsonReader, JsonWriter, T> codec,
    String json
  ) {
    try (JsonReader reader = new JsonReader(new StringReader(json))) {
      return codec.decode(reader);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  public static void writeDouble(JsonWriter writer, double value) throws IOException {
    if (Double.isFinite(value)) {
      writer.value(value);
    } else {
      writer.value(Double.toString(value));
    }
  }

  public static void writeFloat(JsonWriter writer, float value) throws IOException {
    if (Float.isFinite(value)) {
      writer.value(value);
    } else {
      writer.value(Float.toString(value));
    }
  }

  public static double readDouble(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.STRING) {
      return Double.parseDouble(reader.nextString());
    }
    return reader.nextDouble();
  }

  public static float readFloat(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.STRING) {
      return Float.parseFloat(reader.nextString());
    }
    return (float) reader.nextDouble();
  }

  private static void writePrimitive(JsonWriter writer, Comparable<?> value) throws IOException {
    if (value == null) {
      writer.nullValue();
    } else if (value instanceof Boolean) {
      writer.value((Boolean) value);
    } else if (value instanceof Double) {
      writeDouble(writer, (Double) value);
    } else if (value instanceof Float) {
      writeFloat(writer, (Float) value);
    } else if (value instanceof Number) {
      writer.value((Number) value);
    } else {
      writer.value(value.toString());
    }
  }

  private static Comparable<?> readPrimitive(JsonReader reader) throws IOException {
    JsonToken token = reader.peek();
    switch (token) {
      case NULL:
        reader.nextNull();
        return null;
      case BOOLEAN:
        return reader.nextBoolean();
      case STRING:
        return reader.nextString();
      case NUMBER:
        String number = reader.nextString();
        try {
          return Integer.valueOf(number);
        } catch (NumberFormatException ignored) {
          try {
            return Long.valueOf(number);
          } catch (NumberFormatException ignoredAgain) {
            return Double.valueOf(number);
          }
        }
      default:
        throw new IllegalStateException("Expected JSON primitive but found " + token);
    }
  }

  public static <T> void writeNullable(
    JsonWriter writer,
    StreamCodec<JsonReader, JsonWriter, T> codec,
    T value
  ) throws IOException {
    if (value == null) {
      writer.nullValue();
    } else {
      codec.encode(writer, value);
    }
  }

  public static <T> T readNullable(
    JsonReader reader,
    StreamCodec<JsonReader, JsonWriter, T> codec
  ) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    return codec.decode(reader);
  }

  public static final class JsonObjectCodec<T> implements StreamCodec<JsonReader, JsonWriter, T> {
    private final List<JsonField<T, ?>> fields;
    private final List<JsonField<T, ?>> outputFields;
    private final Map<String, Integer> fieldIndices;
    private final Function<Object[], T> constructor;

    private JsonObjectCodec(
      List<? extends JsonField<T, ?>> fields,
      List<? extends JsonField<T, ?>> outputFields,
      Function<Object[], T> constructor
    ) {
      this.fields = new ArrayList<>(fields);
      this.outputFields = new ArrayList<>(outputFields);
      this.constructor = constructor;
      this.fieldIndices = new HashMap<>();
      for (int index = 0; index < this.fields.size(); index++) {
        registerName(this.fields.get(index).name, index);
      }
      for (JsonField<T, ?> outputField : this.outputFields) {
        registerName(outputField.name, -1);
      }
    }

    /**
     * Adds a derived property that is written during encoding and ignored during decoding.
     */
    public JsonObjectCodec<T> encodeOnly(JsonField<T, ?> field) {
      List<JsonField<T, ?>> newOutputFields = new ArrayList<>(outputFields);
      newOutputFields.add(field);
      return new JsonObjectCodec<>(fields, newOutputFields, constructor);
    }

    @Override
    public void encode(JsonWriter writer, T value) {
      try {
        writer.beginObject();
        for (JsonField<T, ?> field : fields) {
          field.encode(writer, value);
        }
        for (JsonField<T, ?> field : outputFields) {
          field.encode(writer, value);
        }
        writer.endObject();
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
    }

    @Override
    public T decode(JsonReader reader) {
      Object[] values = new Object[fields.size()];
      for (int index = 0; index < fields.size(); index++) {
        values[index] = fields.get(index).defaultValue;
      }
      try {
        reader.beginObject();
        while (reader.hasNext()) {
          String name = reader.nextName();
          Integer index = fieldIndices.get(name);
          if (index == null || index < 0) {
            reader.skipValue();
          } else {
            values[index] = fields.get(index).decode(reader);
          }
        }
        reader.endObject();
      } catch (IOException exception) {
        throw new UncheckedIOException(exception);
      }
      return constructor.apply(values);
    }

    private void registerName(String name, int index) {
      if (fieldIndices.put(name, index) != null) {
        throw new IllegalArgumentException("Duplicate JSON field: " + name);
      }
    }
  }

  public static final class JsonValues {
    private final Object[] values;

    private JsonValues(Object[] values) {
      this.values = values;
    }

    @SuppressWarnings("unchecked")
    public <T> T value(int index) {
      return (T) values[index];
    }
  }

  public static final class JsonField<T, V> {
    private final String name;
    private final StreamCodec<JsonReader, JsonWriter, V> codec;
    private final Function<T, V> getter;
    private final V defaultValue;

    private JsonField(
      String name,
      StreamCodec<JsonReader, JsonWriter, V> codec,
      Function<T, V> getter,
      V defaultValue
    ) {
      if (name == null || name.isEmpty()) {
        throw new IllegalArgumentException("JSON field name cannot be empty");
      }
      this.name = name;
      this.codec = codec;
      this.getter = getter;
      this.defaultValue = defaultValue;
    }

    private void encode(JsonWriter writer, T value) throws IOException {
      writer.name(name);
      codec.encode(writer, getter.apply(value));
    }

    private V decode(JsonReader reader) {
      return codec.decode(reader);
    }
  }

  @FunctionalInterface
  public interface HeptaFunction<A, B, C, D, E, F, G, R> {
    R apply(A first, B second, C third, D fourth, E fifth, F sixth, G seventh);
  }

  @FunctionalInterface
  public interface OctaFunction<A, B, C, D, E, F, G, H, R> {
    R apply(A first, B second, C third, D fourth, E fifth, F sixth, G seventh, H eighth);
  }

  @FunctionalInterface
  public interface NonaFunction<A, B, C, D, E, F, G, H, I, R> {
    R apply(A first, B second, C third, D fourth, E fifth, F sixth, G seventh, H eighth, I ninth);
  }

  @FunctionalInterface
  public interface JsonEncoder<T> {
    void encode(JsonWriter writer, T value) throws IOException;
  }

  @FunctionalInterface
  public interface JsonDecoder<T> {
    T decode(JsonReader reader) throws IOException;
  }
}

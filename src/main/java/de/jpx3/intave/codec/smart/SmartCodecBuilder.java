package de.jpx3.intave.codec.smart;

import de.jpx3.intave.codec.StreamCodec;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SmartCodecBuilder<I, O, T> {
	private static final int MAX_FIELD_COUNT = 8192;

	private final StreamCodec<I, O, Integer> lengthCodec;
	private final StreamCodec<I, O, String> nameCodec;
	private final StreamCodec<I, O, byte[]> payloadCodec;
	private final Supplier<O> payloadOutputFactory;
	private final Function<O, byte[]> payloadExtractor;
	private final Function<byte[], I> payloadInputFactory;
	private final Consumer<I> payloadInputCleaner;
	private final Map<String, SmartCodecField<I, O, T, ?>> fields = new LinkedHashMap<>();

	public SmartCodecBuilder(
		StreamCodec<I, O, Integer> lengthCodec,
		StreamCodec<I, O, String> nameCodec,
		StreamCodec<I, O, byte[]> payloadCodec,
		Supplier<O> payloadOutputFactory,
		Function<O, byte[]> payloadExtractor,
		Function<byte[], I> payloadInputFactory,
		Consumer<I> payloadInputCleaner
	) {
		this.lengthCodec = Objects.requireNonNull(lengthCodec, "lengthCodec");
		this.nameCodec = Objects.requireNonNull(nameCodec, "nameCodec");
		this.payloadCodec = Objects.requireNonNull(payloadCodec, "payloadCodec");
		this.payloadOutputFactory = Objects.requireNonNull(payloadOutputFactory, "payloadOutputFactory");
		this.payloadExtractor = Objects.requireNonNull(payloadExtractor, "payloadExtractor");
		this.payloadInputFactory = Objects.requireNonNull(payloadInputFactory, "payloadInputFactory");
		this.payloadInputCleaner = Objects.requireNonNull(payloadInputCleaner, "payloadInputCleaner");
	}

	public <F> SmartCodecBuilder<I, O, T> field(
		String name, StreamCodec<I, O, F> codec, Function<T, F> getter
	) {
		return field(new SmartCodecField<>(name, codec, getter, null));
	}

	public <F> SmartCodecBuilder<I, O, T> field(
		String name, StreamCodec<I, O, F> codec,
		Function<T, F> getter, Supplier<F> fallback
	) {
		return field(new SmartCodecField<>(name, codec, getter, fallback));
	}

	public <F> SmartCodecBuilder<I, O, T> field(
		String name, StreamCodec<I, O, F> codec,
		Function<T, F> getter, F fallback
	) {
		return field(new SmartCodecField<>(name, codec, getter, () -> fallback));
	}

	public ReflectionSmartCodecBuilder<I, O, T> reflectionBuilderOn(Class<T> type) {
		return new ReflectionSmartCodecBuilder<>(this, type);
	}

	public StreamCodec<I, O, T> build(
		Function<SmartCodecDecodeResult, T> constructor
	) {
		Objects.requireNonNull(constructor, "constructor");
		List<SmartCodecField<I, O, T, ?>> codecFields = new ArrayList<>(fields.values());
		return StreamCodec.of(
			(output, value) -> encodeSmartFields(output, value, codecFields),
			input -> constructor.apply(decodeSmartFields(input, codecFields))
		);
	}

	private SmartCodecBuilder<I, O, T> field(
		SmartCodecField<I, O, T, ?> field
	) {
		if (fields.containsKey(field.name())) {
			throw new IllegalArgumentException("Duplicate smart codec field: " + field.name());
		}
		fields.put(field.name(), field);
		return this;
	}

	private void encodeSmartFields(
		O output, T value, List<SmartCodecField<I, O, T, ?>> codecFields
	) {
		if (value == null) {
			throw new IllegalArgumentException("Cannot encode null value");
		}
		lengthCodec.encode(output, codecFields.size());
		for (SmartCodecField<I, O, T, ?> field : codecFields) {
			O payloadOutput = payloadOutputFactory.get();
			field.encode(payloadOutput, value);
			byte[] payload = Objects.requireNonNull(
				payloadExtractor.apply(payloadOutput),
				"payloadExtractor returned null"
			);
			nameCodec.encode(output, field.name());
			payloadCodec.encode(output, payload);
		}
	}

	private SmartCodecDecodeResult decodeSmartFields(
		I input, List<SmartCodecField<I, O, T, ?>> codecFields
	) {
		int fieldCount = lengthCodec.decode(input);
		if (fieldCount < 0) {
			throw new IllegalStateException("Negative smart codec field count: " + fieldCount);
		}
		if (fieldCount > MAX_FIELD_COUNT) {
			throw new IllegalStateException("Smart codec field count exceeds maximum of " + MAX_FIELD_COUNT + ": " + fieldCount);
		}

		Map<String, byte[]> payloads = new HashMap<>(fieldCount);
		for (int j = 0; j < fieldCount; j++) {
			String name = nameCodec.decode(input);
			if (payloads.containsKey(name)) {
				throw new IllegalStateException("Duplicate smart codec field in stream: " + name);
			}
			payloads.put(name, payloadCodec.decode(input));
		}

		Map<String, Object> values = new HashMap<>(codecFields.size());
		for (SmartCodecField<I, O, T, ?> field : codecFields) {
			if (!payloads.containsKey(field.name())) {
				if (!field.hasFallback()) {
					throw new IllegalStateException("Missing smart codec field fallback in stream: " + field.name() + ". Payloads are " + payloads.keySet());
				}
				values.put(field.name(), field.fallback());
				continue;
			}
			byte[] payload = payloads.remove(field.name());
			I payloadInput = payloadInputFactory.apply(payload);
			try {
				values.put(field.name(), field.decode(payloadInput));
			} finally {
				payloadInputCleaner.accept(payloadInput);
			}
		}
		return new SmartCodecDecodeResult(values, payloads);
	}

	static final class SmartCodecField<I, O, T, F> {
		private final String name;
		private final StreamCodec<I, O, F> codec;
		private final Function<T, F> getter;
		private final Supplier<F> fallback;

		SmartCodecField(
			String name,
			StreamCodec<I, O, F> codec,
			Function<T, F> getter,
			Supplier<F> fallback
		) {
			this.name = Objects.requireNonNull(name, "name");
			this.codec = Objects.requireNonNull(codec, "codec");
			this.getter = Objects.requireNonNull(getter, "getter");
			this.fallback = fallback;
		}

		String name() {
			return name;
		}

		void encode(O output, T value) {
			codec.encode(output, getter.apply(value));
		}

		Object decode(I input) {
			return codec.decode(input);
		}

		boolean hasFallback() {
			return fallback != null;
		}

		Object fallback() {
			return fallback.get();
		}
	}
}

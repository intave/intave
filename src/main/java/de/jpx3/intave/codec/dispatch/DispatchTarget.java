package de.jpx3.intave.codec.dispatch;

import de.jpx3.intave.codec.StreamCodec;

import java.util.Objects;
import java.util.function.Supplier;

final class DispatchTarget<I, O, K, T> {
	private final K key;
	private final Class<? extends T> type;
	private final Supplier<? extends StreamCodec<I, O, ? extends T>> codecSupplier;

	DispatchTarget(
		K key, Class<? extends T> type,
		Supplier<? extends StreamCodec<I, O, ? extends T>> codecSupplier
	) {
		this.key = key;
		this.type = type;
		this.codecSupplier = codecSupplier;
	}

	K key() {
		return key;
	}

	Class<? extends T> type() {
		return type;
	}

	boolean matches(T value) {
		return type.isInstance(value);
	}

	void encode(O output, T value) {
		//noinspection unchecked
		((StreamCodec<I, O, T>) codec()).encode(output, value);
	}

	T decode(I input) {
		return codec().decode(input);
	}

	StreamCodec<I, O, ? extends T> codec() {
		StreamCodec<I, O, ? extends T> codec = codecSupplier.get();
		return Objects.requireNonNull(codec, "Dispatch codec supplier returned null for key: " + key);
	}
}

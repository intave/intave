package de.jpx3.intave.codec.smart;

import de.jpx3.intave.codec.StreamCodec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReflectionSmartCodecBuilder<I, O, T> {
	private final SmartCodecBuilder<I, O, T> builder;
	private final Class<T> type;
	private final List<String> fieldNames = new ArrayList<>();

	ReflectionSmartCodecBuilder(
		SmartCodecBuilder<I, O, T> builder,
		Class<T> type
	) {
		this.builder = Objects.requireNonNull(builder, "builder");
		this.type = Objects.requireNonNull(type, "type");
	}

	public <F> ReflectionSmartCodecBuilder<I, O, T> field(
		String name,
		StreamCodec<I, O, F> codec
	) {
		builder.field(name, codec, reflectedGetter(name));
		fieldNames.add(name);
		return this;
	}

	public <F> ReflectionSmartCodecBuilder<I, O, T> field(
		String name,
		StreamCodec<I, O, F> codec,
		Supplier<F> fallback
	) {
		builder.field(name, codec, reflectedGetter(name), fallback);
		fieldNames.add(name);
		return this;
	}

	public StreamCodec<I, O, T> build() {
		return build(findConstructor(fieldNames.size()));
	}

	public StreamCodec<I, O, T> build(
		Class<?>... parameterTypes
	) {
		try {
			Constructor<T> constructor = type.getDeclaredConstructor(parameterTypes);
			return build(constructor);
		} catch (NoSuchMethodException exception) {
			throw new IllegalStateException("Cannot find constructor for " + type.getName(), exception);
		}
	}

	public StreamCodec<I, O, T> build(
		Constructor<T> constructor
	) {
		Objects.requireNonNull(constructor, "constructor");
		constructor.setAccessible(true);
		return builder.build(values -> construct(constructor, values));
	}

	@SuppressWarnings("unchecked")
	private <F> Function<T, F> reflectedGetter(
		String name
	) {
		Field field = findField(name);
		field.setAccessible(true);
		return value -> {
			try {
				return (F) field.get(value);
			} catch (IllegalAccessException exception) {
				throw new IllegalStateException("Cannot read field " + name + " from " + type.getName(), exception);
			}
		};
	}

	private Field findField(String name) {
		Class<?> currentType = type;
		while (currentType != null) {
			try {
				return currentType.getDeclaredField(name);
			} catch (NoSuchFieldException ignored) {
				currentType = currentType.getSuperclass();
			}
		}
		throw new IllegalStateException("Cannot find field " + name + " in " + type.getName());
	}

	@SuppressWarnings("unchecked")
	private Constructor<T> findConstructor(int parameterCount) {
		Constructor<?> match = null;
		for (Constructor<?> constructor : type.getDeclaredConstructors()) {
			if (constructor.getParameterTypes().length != parameterCount) {
				continue;
			}
			if (match != null) {
				throw new IllegalStateException(
					"Multiple constructors with " + parameterCount + " parameters found in " + type.getName()
				);
			}
			match = constructor;
		}
		if (match == null) {
			throw new IllegalStateException(
				"Cannot find constructor with " + parameterCount + " parameters in " + type.getName()
			);
		}
		return (Constructor<T>) match;
	}

	private T construct(Constructor<T> constructor, SmartCodecDecodeResult values) {
		Object[] arguments = new Object[fieldNames.size()];
		for (int i = 0; i < fieldNames.size(); i++) {
			arguments[i] = values.get(fieldNames.get(i));
		}
		try {
			return constructor.newInstance(arguments);
		} catch (Exception exception) {
			throw new IllegalStateException("Cannot construct " + type.getName(), exception);
		}
	}
}

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

package de.jpx3.intave.player.attribute;

import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.*;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

public final class Attribute {
	private static final StreamCodec<ByteBuf, ByteBuf, List<AttributeModifier>> MODIFIERS_CODEC =
		listCodecOf(AttributeModifier.STREAM_CODEC);

	public static final StreamCodec<ByteBuf, ByteBuf, Attribute> STREAM_CODEC = StreamCodec.of(
		(buffer, attribute) -> {
			STRING.encode(buffer, attribute.attributeKey);
			DOUBLE.encode(buffer, attribute.defaultBaseValue);
			DOUBLE.encode(buffer, attribute.baseValue);
			MODIFIERS_CODEC.encode(
				buffer,
				new ArrayList<>(attribute.attributeModifiers)
			);
		},
		buffer -> Attribute.newBuilder()
			.withAttributeKey(STRING.decode(buffer))
			.withDefaultBaseValue(DOUBLE.decode(buffer))
			.withBaseValue(DOUBLE.decode(buffer))
			.withAttributeModifiers(new HashSet<>(
				MODIFIERS_CODEC.decode(buffer)
			))
			.build()
	);

	private final String attributeKey;
	private final double defaultBaseValue;
	private double baseValue;
	private final Set<AttributeModifier> attributeModifiers = new HashSet<>();

	public Attribute(String attributeKey, double defaultBaseValue) {
		this.attributeKey = attributeKey;
		this.defaultBaseValue = defaultBaseValue;
	}

	public static Attribute fromProtocolLib(
		com.comphenix.protocol.wrappers.WrappedAttribute attribute
	) {
		Attribute wrappedAttribute = new Attribute(attribute.getAttributeKey(), attribute.getBaseValue());
		wrappedAttribute.baseValue = attribute.getBaseValue();
		wrappedAttribute.attributeModifiers.addAll(AttributeModifier.fromProtocolLib(attribute.getModifiers()));
		return wrappedAttribute;
	}

	public String attributeKey() {
		return attributeKey;
	}

	public double baseValue() {
		return baseValue;
	}

	public Set<AttributeModifier> modifiers() {
		return Collections.unmodifiableSet(attributeModifiers);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Attribute)) return false;
		Attribute other = (Attribute) obj;
		return Double.compare(defaultBaseValue, other.defaultBaseValue) == 0
			&& Double.compare(baseValue, other.baseValue) == 0
			&& Objects.equals(attributeKey, other.attributeKey)
			&& attributeModifiers.equals(other.attributeModifiers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(attributeKey, defaultBaseValue, baseValue, attributeModifiers);
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static Builder newBuilder(Attribute attribute) {
		return new Builder()
			.withAttributeKey(attribute.attributeKey)
			.withDefaultBaseValue(attribute.defaultBaseValue)
			.withBaseValue(attribute.baseValue)
			.withAttributeModifiers(attribute.attributeModifiers);
	}

	public static class Builder {
		private String attributeKey;
		private double defaultBaseValue;
		private double baseValue;
		private final Set<AttributeModifier> attributeModifiers = new HashSet<>();

		public Builder withAttributeKey(String attributeKey) {
			this.attributeKey = attributeKey;
			return this;
		}

		public Builder withDefaultBaseValue(double defaultBaseValue) {
			this.defaultBaseValue = defaultBaseValue;
			return this;
		}

		public Builder withBaseValue(double baseValue) {
			this.baseValue = baseValue;
			return this;
		}

		public Builder withAttributeModifiers(Set<AttributeModifier> attributeModifiers) {
			this.attributeModifiers.clear();
			this.attributeModifiers.addAll(attributeModifiers);
			return this;
		}

		public Attribute build() {
			Attribute attribute = new Attribute(attributeKey, defaultBaseValue);
			attribute.baseValue = baseValue;
			attribute.attributeModifiers.addAll(attributeModifiers);
			return attribute;
		}
	}
}

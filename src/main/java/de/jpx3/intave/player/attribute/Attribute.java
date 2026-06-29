package de.jpx3.intave.player.attribute;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Attribute {
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

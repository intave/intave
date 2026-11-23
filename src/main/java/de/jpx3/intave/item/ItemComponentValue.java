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

package de.jpx3.intave.item;

import com.viaversion.nbt.tag.Tag;

import java.util.*;

/**
 * An immutable snapshot of a decoded component value. Dynamic registry references
 * retain the IDs assigned by the connection. Item templates expose their item key,
 * count and component patch through the fields "item", "count" and "components".
 */
public final class ItemComponentValue {
	public static final ItemComponentValue EMPTY = fields(Collections.emptyMap());
	private final Object value;

	private ItemComponentValue(Object value) {
		this.value = value;
	}

	public static ItemComponentValue of(Object value) {
		Objects.requireNonNull(value, "value");
		if (value instanceof Tag) {
			return new ItemComponentValue(((Tag) value).copy());
		}
		if (value instanceof ItemStack) {
			return new ItemComponentValue(((ItemStack) value).copy());
		}
		if (value instanceof ItemComponentPatch || value instanceof String || value instanceof Boolean || value instanceof Integer
			|| value instanceof Long || value instanceof Float || value instanceof Double || value instanceof UUID) {
			return new ItemComponentValue(value);
		}
		throw new IllegalArgumentException("Unsupported component value " + value.getClass());
	}

	public static ItemComponentValue fields(Map<String, ItemComponentValue> fields) {
		Map<String, ItemComponentValue> copy = new LinkedHashMap<>();
		fields.forEach((key, value) -> copy.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
		return new ItemComponentValue(Collections.unmodifiableMap(copy));
	}

	public static ItemComponentValue list(List<ItemComponentValue> elements) {
		List<ItemComponentValue> copy = new ArrayList<>(elements);
		copy.forEach(Objects::requireNonNull);
		return new ItemComponentValue(Collections.unmodifiableList(copy));
	}

	@SuppressWarnings("unchecked")
	public Map<String, ItemComponentValue> fields() {
		return (Map<String, ItemComponentValue>) value;
	}

	public ItemComponentValue field(String name) {
		return fields().get(name);
	}

	public boolean isEmpty() {
		return value instanceof Map && ((Map<?, ?>) value).isEmpty();
	}

	@SuppressWarnings("unchecked")
	public List<ItemComponentValue> elements() {
		return (List<ItemComponentValue>) value;
	}

	public Number number() {
		return (Number) value;
	}

	public String string() {
		return (String) value;
	}

	public boolean bool() {
		return (Boolean) value;
	}

	public UUID uuid() {
		return (UUID) value;
	}

	public Tag nbt() {
		return ((Tag) value).copy();
	}

	public ItemStack item() {
		return ((ItemStack) value).copy();
	}

	public ItemComponentPatch patch() {
		return (ItemComponentPatch) value;
	}
}

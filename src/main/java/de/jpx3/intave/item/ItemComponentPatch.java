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

import de.jpx3.intave.share.MinecraftKey;

import java.util.*;

/**
 * Explicit component changes carried on the wire, without inventing item defaults.
 */
public final class ItemComponentPatch {
	public static final ItemComponentPatch EMPTY = new ItemComponentPatch(Collections.emptyMap(), Collections.emptySet());
	private final Map<MinecraftKey, ItemComponentValue> added;
	private final Set<MinecraftKey> removed;

	public ItemComponentPatch(Map<MinecraftKey, ItemComponentValue> added, Set<MinecraftKey> removed) {
		Map<MinecraftKey, ItemComponentValue> copy = new LinkedHashMap<>();
		added.forEach((key, value) -> copy.put(Objects.requireNonNull(key), Objects.requireNonNull(value)));
		Set<MinecraftKey> removals = new LinkedHashSet<>(removed);
		removals.forEach(key -> {
			Objects.requireNonNull(key);
			if (copy.containsKey(key)) throw new IllegalArgumentException("Component is both added and removed: " + key);
		});
		this.added = Collections.unmodifiableMap(copy);
		this.removed = Collections.unmodifiableSet(removals);
	}

	public Map<MinecraftKey, ItemComponentValue> added() {
		return added;
	}

	public Set<MinecraftKey> removed() {
		return removed;
	}

	public ItemComponentValue get(MinecraftKey key) {
		return added.get(key);
	}

	public ItemComponentPatch with(MinecraftKey key, ItemComponentValue value) {
		Map<MinecraftKey, ItemComponentValue> additions = new LinkedHashMap<>(added);
		Set<MinecraftKey> removals = new LinkedHashSet<>(removed);
		additions.put(key, value);
		removals.remove(key);
		return new ItemComponentPatch(additions, removals);
	}

	public ItemComponentPatch without(MinecraftKey key) {
		Map<MinecraftKey, ItemComponentValue> additions = new LinkedHashMap<>(added);
		Set<MinecraftKey> removals = new LinkedHashSet<>(removed);
		additions.remove(key);
		removals.add(key);
		return new ItemComponentPatch(additions, removals);
	}
}

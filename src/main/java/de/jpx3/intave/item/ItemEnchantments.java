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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ItemEnchantments {
	public static final ItemEnchantments EMPTY = new ItemEnchantments(Collections.emptyMap());

	private final Map<MinecraftKey, Integer> levels;

	private ItemEnchantments(Map<MinecraftKey, Integer> levels) {
		this.levels = levels;
	}

	public static ItemEnchantments of(Map<MinecraftKey, Integer> levels) {
		if (levels.isEmpty()) {
			return EMPTY;
		}
		Map<MinecraftKey, Integer> copy = new LinkedHashMap<>();
		levels.forEach((key, level) -> copy.put(
			Objects.requireNonNull(key, "enchantment"), Objects.requireNonNull(level, "level")
		));
		return new ItemEnchantments(Collections.unmodifiableMap(copy));
	}

	public Map<MinecraftKey, Integer> levels() {
		return levels;
	}

	public boolean hasEnchantments() {
		return !levels.isEmpty();
	}

	public boolean hasEnchantment(MinecraftKey enchantment) {
		return levels.containsKey(enchantment);
	}

	public int enchantmentLevel(MinecraftKey enchantment) {
		return levels.getOrDefault(enchantment, 0);
	}
}

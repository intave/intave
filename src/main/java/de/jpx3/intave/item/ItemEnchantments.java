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

import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.NumberTag;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.MinecraftKey;

import java.util.*;

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

	public static ItemEnchantments fromNbt(@Nullable ListTag<CompoundTag> list, int protocol) {
		if (list == null) {
			return EMPTY;
		}
		Map<MinecraftKey, Integer> levels = new LinkedHashMap<>();
		for (CompoundTag entry : list) {
			MinecraftKey key;
			if (protocol < 393) {
				NumberTag id = entry.getNumberTag("id");
				key = id == null ? null : EnchantmentRegistry.forProtocol(protocol).keyById(id.asShort());
			} else {
				String id = entry.getString("id", "");
				key = id.isEmpty() ? null : MinecraftKey.parse(id);
			}
			NumberTag level = entry.getNumberTag("lvl");
			if (key != null) {
				levels.put(key, level == null ? 0 : protocol < 393 ? (int) level.asShort() : level.asInt());
			}
		}
		return of(levels);
	}

	/** Returns a detached NBT list in the protocol's enchantment format. */
	public ListTag<CompoundTag> toNbt(int protocol) {
		ListTag<CompoundTag> list = new ListTag<>(CompoundTag.class);
		for (Map.Entry<MinecraftKey, Integer> enchantment : levels.entrySet()) {
			CompoundTag entry = new CompoundTag();
			if (protocol < 393) {
				int id = EnchantmentRegistry.forProtocol(protocol).idOf(enchantment.getKey());
				if (id < 0) {
					throw new IllegalArgumentException("Unknown legacy enchantment " + enchantment.getKey());
				}
				entry.putShort("id", (short) id);
			} else {
				entry.putString("id", enchantment.getKey().fullKey());
			}
			int level = enchantment.getValue();
			if (protocol < 393 && (level < Short.MIN_VALUE || level > Short.MAX_VALUE)) {
				throw new IllegalArgumentException("Legacy enchantment level does not fit a short: " + level);
			}
			if (level >= Short.MIN_VALUE && level <= Short.MAX_VALUE) {
				entry.putShort("lvl", (short) level);
			} else {
				entry.putInt("lvl", level);
			}
			list.add(entry);
		}
		return list;
	}

	public Map<MinecraftKey, Integer> levels() {
		return levels;
	}

	static ItemEnchantments fromComponent(ItemComponentValue component, EnchantmentRegistry registry) {
		if (component == null) {
			return EMPTY;
		}
		Map<MinecraftKey, Integer> levels = new LinkedHashMap<>();
		for (ItemComponentValue entry : component.field("enchantments").elements()) {
			MinecraftKey key = registry.keyById(entry.field("id").number().intValue());
			if (key != null) {
				levels.put(key, entry.field("level").number().intValue());
			}
		}
		return of(levels);
	}

	ItemComponentValue toComponent(EnchantmentRegistry registry, @Nullable Boolean showInTooltip) {
		List<ItemComponentValue> entries = new ArrayList<>();
		levels.forEach((key, level) -> {
			int id = registry.idOf(key);
			if (id < 0) {
				throw new IllegalArgumentException("Unknown enchantment " + key);
			}
			Map<String, ItemComponentValue> entry = new LinkedHashMap<>();
			entry.put("id", ItemComponentValue.of(id));
			entry.put("level", ItemComponentValue.of(level));
			entries.add(ItemComponentValue.fields(entry));
		});
		Map<String, ItemComponentValue> fields = new LinkedHashMap<>();
		fields.put("enchantments", ItemComponentValue.list(entries));
		if (showInTooltip != null) {
			fields.put("show_in_tooltip", ItemComponentValue.of(showInTooltip));
		}
		return ItemComponentValue.fields(fields);
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

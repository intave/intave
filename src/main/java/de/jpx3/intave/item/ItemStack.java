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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.MinecraftKey;

import java.util.Objects;

public final class ItemStack {
	private static final MinecraftKey AIR = MinecraftKey.withDefaultNamespace("air");

	private MinecraftKey type;
	private int amount;
	private ItemEnchantments enchantments = ItemEnchantments.EMPTY;
	private @Nullable String skullOwnerName;
	private @Nullable Short legacyData;

	public ItemStack(MinecraftKey type, int amount) {
		setType(type);
		setAmount(amount);
	}

	public MinecraftKey type() {
		return type;
	}

	public void setType(MinecraftKey type) {
		Objects.requireNonNull(type, "type");
		if (AIR.equals(type)) {
			throw new IllegalArgumentException("An item stack must contain an item");
		}
		this.type = type;
	}

	public int amount() {
		return amount;
	}

	public void setAmount(int amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Item amount must be positive");
		}
		this.amount = amount;
	}

	public ItemEnchantments enchantments() {
		return enchantments;
	}

	public void setEnchantments(ItemEnchantments enchantments) {
		this.enchantments = Objects.requireNonNull(enchantments, "enchantments");
	}

	public boolean hasSkullOwnerName() {
		return skullOwnerName != null;
	}

	public @Nullable String skullOwnerName() {
		return skullOwnerName;
	}

	public void setSkullOwnerName(@Nullable String skullOwnerName) {
		this.skullOwnerName = skullOwnerName;
	}

	public boolean hasLegacyData() {
		return legacyData != null;
	}

	/**
	 * The raw pre-flattening damage or variant value, including zero when present.
	 */
	public @Nullable Short legacyData() {
		return legacyData;
	}

	public void setLegacyData(@Nullable Short legacyData) {
		this.legacyData = legacyData;
	}

	/**
	 * Returns an independent copy, including all data outside the exposed fields.
	 */
	public ItemStack copy() {
		ItemStack copy = new ItemStack(type, amount);
		copy.enchantments = enchantments;
		copy.skullOwnerName = skullOwnerName;
		copy.legacyData = legacyData;
		return copy;
	}
}

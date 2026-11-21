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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ItemStackTest {
	@Test
	void enchantmentsKeepZeroLevelsAndSnapshotTheInput() {
		MinecraftKey sharpness = MinecraftKey.withDefaultNamespace("sharpness");
		MinecraftKey custom = new MinecraftKey("example", "sharpness");
		Map<MinecraftKey, Integer> levels = new HashMap<>();
		levels.put(sharpness, 0);
		levels.put(custom, 3);
		ItemEnchantments enchantments = ItemEnchantments.of(levels);
		levels.clear();

		assertTrue(enchantments.hasEnchantments());
		assertTrue(enchantments.hasEnchantment(sharpness));
		assertEquals(0, enchantments.enchantmentLevel(sharpness));
		assertEquals(3, enchantments.enchantmentLevel(custom));
		assertFalse(ItemEnchantments.EMPTY.hasEnchantment(sharpness));
		assertEquals(0, ItemEnchantments.EMPTY.enchantmentLevel(sharpness));
		assertThrows(UnsupportedOperationException.class, () -> enchantments.levels().clear());
	}

	@Test
	void copyingAStackPreservesOptionalDataAndIsolatesEdits() {
		MinecraftKey head = MinecraftKey.withDefaultNamespace("skull");
		MinecraftKey enchantment = MinecraftKey.withDefaultNamespace("protection");
		ItemStack stack = new ItemStack(head, 2);
		assertFalse(stack.hasLegacyData());
		assertFalse(stack.hasSkullOwnerName());
		stack.setLegacyData((short) 0);
		stack.setSkullOwnerName("Alex");
		stack.setEnchantments(ItemEnchantments.of(Collections.singletonMap(enchantment, 2)));

		ItemStack copy = stack.copy();
		assertTrue(copy.hasLegacyData());
		assertEquals((short) 0, copy.legacyData().shortValue());
		assertEquals("Alex", copy.skullOwnerName());
		assertEquals(2, copy.enchantments().enchantmentLevel(enchantment));
		copy.setLegacyData(null);
		copy.setSkullOwnerName(null);
		copy.setEnchantments(ItemEnchantments.EMPTY);
		copy.setType(MinecraftKey.withDefaultNamespace("stone"));
		copy.setAmount(1);

		assertEquals(head, stack.type());
		assertEquals(2, stack.amount());
		assertTrue(stack.hasLegacyData());
		assertEquals("Alex", stack.skullOwnerName());
		assertTrue(stack.enchantments().hasEnchantment(enchantment));
	}

	@Test
	void mutationCannotTurnAPresentStackIntoAnEmptySlot() {
		MinecraftKey stone = MinecraftKey.withDefaultNamespace("stone");
		MinecraftKey air = MinecraftKey.withDefaultNamespace("air");
		ItemStack stack = new ItemStack(stone, 1);
		assertThrows(IllegalArgumentException.class, () -> new ItemStack(air, 1));
		assertThrows(IllegalArgumentException.class, () -> new ItemStack(stone, -1));
		assertThrows(IllegalArgumentException.class, () -> stack.setType(air));
		assertThrows(IllegalArgumentException.class, () -> stack.setAmount(0));
		assertEquals(stone, stack.type());
		assertEquals(1, stack.amount());
	}
}

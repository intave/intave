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
import de.jpx3.intave.share.MinecraftKey;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
		ItemStack stack = new ItemStack(head, 2, 47);
		assertFalse(stack.hasLegacyData());
		assertFalse(stack.hasSkullOwnerName());
		assertEquals(Optional.empty(), stack.skullOwnerName());
		stack.setSkullOwnerName(null);
		assertNull(stack.nbt());
		stack.setLegacyData((short) 0);
		stack.setSkullOwnerName("Alex");
		assertEquals("Alex", stack.nbt().getString("SkullOwner"));
		stack.nbt().putString("SkullOwner", "detached");
		assertEquals(Optional.of("Alex"), stack.skullOwnerName());
		stack.setEnchantments(ItemEnchantments.of(Collections.singletonMap(enchantment, 2)));

		ItemStack copy = stack.copy();
		assertTrue(copy.hasLegacyData());
		assertEquals((short) 0, copy.legacyData().shortValue());
		assertEquals(Optional.of("Alex"), copy.skullOwnerName());
		assertEquals(2, copy.enchantments().enchantmentLevel(enchantment));
		copy.setLegacyData(null);
		copy.setSkullOwnerName(null);
		assertEquals(Optional.empty(), copy.skullOwnerName());
		assertNull(copy.nbt().get("SkullOwner"));
		copy.setEnchantments(ItemEnchantments.EMPTY);
		copy.setType(MinecraftKey.withDefaultNamespace("stone"));
		copy.setAmount(1);

		assertEquals(head, stack.type());
		assertEquals(2, stack.amount());
		assertTrue(stack.hasLegacyData());
		assertEquals(Optional.of("Alex"), stack.skullOwnerName());
		assertTrue(stack.enchantments().hasEnchantment(enchantment));
	}

	@Test
	void enchantmentEditsUpdateTheOwnedTagImmediatelyAndPreserveItOnFailure() {
		MinecraftKey sharpness = MinecraftKey.withDefaultNamespace("sharpness");
		for (int protocol : new int[]{47, 393, 404, 754}) {
			for (String type : new String[]{"diamond_sword", "enchanted_book"}) {
				ItemStack stack = new ItemStack(MinecraftKey.withDefaultNamespace(type), 1, protocol);
				stack.setEnchantments(ItemEnchantments.EMPTY);
				assertNull(stack.nbt());
				stack.setSkullOwnerName("Alex");
				stack.setEnchantments(ItemEnchantments.of(Collections.singletonMap(sharpness, 2)));
				String name = type.equals("enchanted_book") ? "StoredEnchantments" : protocol < 393 ? "ench" : "Enchantments";
				CompoundTag entry = stack.nbt().getListTag(name, CompoundTag.class).get(0);
				assertEquals(2, entry.getShort("lvl"));
				if (protocol < 393) assertEquals(16, entry.getShort("id"));
				else assertEquals("minecraft:sharpness", entry.getString("id"));
				assertEquals(2, stack.enchantments().enchantmentLevel(sharpness));
				if (protocol < 393) {
					CompoundTag before = stack.nbt();
					assertThrows(IllegalArgumentException.class, () -> stack.setEnchantments(
						ItemEnchantments.of(Collections.singletonMap(sharpness, 32768))
					));
					assertEquals(before, stack.nbt());
				}
				ItemStack copy = stack.copy();
				copy.setEnchantments(ItemEnchantments.EMPTY);
				assertNull(copy.nbt().get(name));
				assertFalse(copy.enchantments().hasEnchantments());
				assertEquals(Optional.of("Alex"), copy.skullOwnerName());
				assertEquals(2, stack.enchantments().enchantmentLevel(sharpness));
			}
		}
	}

	@Test
	void replacingTheTagAndChangingItemTypeImmediatelyChangesEnchantmentReads() {
		MinecraftKey sharpness = MinecraftKey.withDefaultNamespace("sharpness");
		for (int protocol : new int[]{47, 393, 404, 754}) {
			ItemStack stack = new ItemStack(MinecraftKey.withDefaultNamespace("diamond_sword"), 1, protocol);
			stack.setEnchantments(ItemEnchantments.of(Collections.singletonMap(sharpness, 1)));
			ItemEnchantments previous = stack.enchantments();
			CompoundTag tag = new CompoundTag();
			CompoundTag entry = new CompoundTag();
			if (protocol < 393) entry.putShort("id", (short) 16);
			else entry.putString("id", "minecraft:sharpness");
			entry.putShort("lvl", (short) 3);
			ListTag<CompoundTag> applied = new ListTag<>(CompoundTag.class);
			applied.add(entry);
			tag.put(protocol < 393 ? "ench" : "Enchantments", applied);
			CompoundTag storedEntry = entry.copy();
			storedEntry.putShort("lvl", (short) 7);
			ListTag<CompoundTag> stored = new ListTag<>(CompoundTag.class);
			stored.add(storedEntry);
			tag.put("StoredEnchantments", stored);
			stack.setTag(tag);
			tag.clear();
			assertEquals(1, previous.enchantmentLevel(sharpness));
			assertEquals(3, stack.enchantments().enchantmentLevel(sharpness));
			stack.setType(MinecraftKey.withDefaultNamespace("enchanted_book"));
			assertEquals(7, stack.enchantments().enchantmentLevel(sharpness));
			stack.setEnchantments(ItemEnchantments.EMPTY);
			assertNull(stack.nbt().get("StoredEnchantments"));
			stack.setType(MinecraftKey.withDefaultNamespace("diamond_sword"));
			assertEquals(3, stack.enchantments().enchantmentLevel(sharpness));
			stack.setTag(null);
			assertNull(stack.nbt());
			assertFalse(stack.enchantments().hasEnchantments());
		}
	}

	@Test
	void mutationCannotTurnAPresentStackIntoAnEmptySlot() {
		MinecraftKey stone = MinecraftKey.withDefaultNamespace("stone");
		MinecraftKey air = MinecraftKey.withDefaultNamespace("air");
		ItemStack stack = new ItemStack(stone, 1, 754);
		assertThrows(IllegalArgumentException.class, () -> new ItemStack(air, 1, 754));
		assertThrows(IllegalArgumentException.class, () -> new ItemStack(stone, -1, 754));
		assertThrows(IllegalArgumentException.class, () -> stack.setType(air));
		assertThrows(IllegalArgumentException.class, () -> stack.setAmount(0));
		assertEquals(stone, stack.type());
		assertEquals(1, stack.amount());
	}
}

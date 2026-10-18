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

package de.jpx3.intave.module.test.record;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MovementFrameStateItemStateTest {
	@Test
	void reconstructsLegacyItemWithoutBukkitServer() {
		assertNull(Bukkit.getServer());
		Material woodenSword = Material.getMaterial("LEGACY_WOOD_SWORD");
		assertNotNull(woodenSword);

		MovementFrameState.ItemState state = new MovementFrameState.ItemState(
			woodenSword, 2, 0, Map.of()
		);

		ItemStack item = state.toItemStack();

		assertSame(woodenSword, item.getType());
		assertEquals(2, item.getAmount());
	}
}

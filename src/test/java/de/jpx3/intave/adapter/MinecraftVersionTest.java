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

package de.jpx3.intave.adapter;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_21_3;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinecraftVersionTest {
	@Test
	void atOrAboveReflectsCurrentVersionChanges() {
		try {
			MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
			assertFalse(VER1_21_3.atOrAbove());

			MinecraftVersion.setCurrent(new MinecraftVersion("26.1.2"));
			assertTrue(VER1_21_3.atOrAbove());

			MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
			assertFalse(VER1_21_3.atOrAbove());
		} finally {
			MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
		}
	}
}

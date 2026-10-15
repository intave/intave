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

package de.jpx3.intave.check.movement.physics.environment;

import org.junit.jupiter.api.Test;
import org.bukkit.Material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttackCooldownTest {
	@Test
	void invalidAttackSpeedsCannotProveAChargedAttack() {
		AttackCooldown cooldown = new AttackCooldown();
		for (int tick = 0; tick < 30; tick++) cooldown.tick();
		for (double speed : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
			assertFalse(cooldown.definitelyCharged(speed));
		}
	}

	@Test
	void slowerWeaponsUseTheirOwnRechargeDelay() {
		AttackCooldown cooldown = new AttackCooldown();
		for (int tick = 0; tick < 10; tick++) cooldown.tick();
		assertFalse(cooldown.definitelyCharged(1.6D));
		cooldown.tick();
		assertTrue(cooldown.definitelyCharged(1.6D));
	}
	@Test
	void usesStrictClientChargeBoundary() {
		AttackCooldown cooldown = new AttackCooldown();

		for (int tick = 0; tick < 4; tick++) {
			cooldown.tick();
		}

		assertFalse(cooldown.definitelyCharged(4.0D));
		cooldown.tick();
		assertTrue(cooldown.definitelyCharged(4.0D));
	}

	@Test
	void attackResetsTheLowerBound() {
		AttackCooldown cooldown = new AttackCooldown();
		for (int tick = 0; tick < 5; tick++) {
			cooldown.tick();
		}

		cooldown.reset();

		assertEquals(0, cooldown.minimumTicksSinceLastAttack());
		assertFalse(cooldown.definitelyCharged(4.0D));
	}

	@Test
	void viaVersionAttackSpeedChargesAfterOneTick() {
		AttackCooldown cooldown = new AttackCooldown();

		assertFalse(cooldown.definitelyCharged(20.0D));
		cooldown.tick();
		assertTrue(cooldown.definitelyCharged(20.0D));
	}

	@Test
	void sameTickSlotOscillationPreservesChargedAttack() {
		AttackCooldown cooldown = chargedCooldown(Material.DIAMOND_SWORD, (short) 0, true, false);
		int chargedTicks = cooldown.minimumTicksSinceLastAttack();

		cooldown.observeHeldItem(Material.STONE, (short) 0, false, false);
		cooldown.observeHeldItem(Material.DIAMOND_SWORD, (short) 0, true, false);
		cooldown.tick();

		assertEquals(chargedTicks + 1, cooldown.minimumTicksSinceLastAttack());
		assertTrue(cooldown.definitelyCharged(4.0D));
	}

	@Test
	void persistentSlotChangeResetsOnTheNextClientTick() {
		AttackCooldown cooldown = chargedCooldown(Material.DIAMOND_SWORD, (short) 0, true, false);

		cooldown.observeHeldItem(Material.STONE, (short) 0, false, false);
		cooldown.tick();

		assertEquals(0, cooldown.minimumTicksSinceLastAttack());
		assertFalse(cooldown.definitelyCharged(4.0D));
	}

	@Test
	void legacyDataValueChangeResetsForNonDamageableItems() {
		AttackCooldown cooldown = chargedCooldown(
			Material.STONE, (short) 0, false, true
		);

		cooldown.observeHeldItem(Material.STONE, (short) 1, false, true);
		cooldown.tick();

		assertEquals(0, cooldown.minimumTicksSinceLastAttack());
	}

	@Test
	void durabilityChangeDoesNotResetForDamageableItems() {
		AttackCooldown cooldown = chargedCooldown(
			Material.DIAMOND_SWORD, (short) 0, true, true
		);

		cooldown.observeHeldItem(Material.DIAMOND_SWORD, (short) 1, true, true);
		cooldown.tick();

		assertTrue(cooldown.definitelyCharged(4.0D));
	}

	private static AttackCooldown chargedCooldown(
		Material material, short data, boolean damageable, boolean legacyDataValues
	) {
		AttackCooldown cooldown = new AttackCooldown();
		cooldown.observeHeldItem(material, data, damageable, legacyDataValues);
		cooldown.tick();
		for (int tick = 0; tick < 5; tick++) {
			cooldown.observeHeldItem(material, data, damageable, legacyDataValues);
			cooldown.tick();
		}
		assertTrue(cooldown.definitelyCharged(4.0D));
		return cooldown;
	}
}

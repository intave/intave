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

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Nullable;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class AttackCooldown {
	private int minimumTicksSinceLastAttack;
	private Material lastItemInMainHand = Material.AIR;
	private short lastItemData;
	private Material pendingItemInMainHand = Material.AIR;
	private short pendingItemData;

	public int minimumTicksSinceLastAttack() {
		return minimumTicksSinceLastAttack;
	}

	public void tick() {
		if (minimumTicksSinceLastAttack < Integer.MAX_VALUE) {
			minimumTicksSinceLastAttack++;
		}
		if (lastItemInMainHand != pendingItemInMainHand
			|| lastItemData != pendingItemData) {
			reset();
			lastItemInMainHand = pendingItemInMainHand;
			lastItemData = pendingItemData;
		}
	}

	/**
	 * Records the final main-hand item that the client will observe on its next
	 * player tick. Multiple slot changes before that tick intentionally replace
	 * this value: the client increments its cooldown and compares only the final
	 * main-hand stack, rather than resetting for every held-slot packet.
	 */
	public void observeHeldItem(@Nullable ItemStack itemStack) {
		observeHeldItem(itemStack, !MinecraftVersions.VER1_13_0.atOrAbove());
	}

	void observeHeldItem(@Nullable ItemStack itemStack, boolean legacyDataValues) {
		if (itemStack == null || itemStack.getAmount() <= 0
			|| itemStack.getType() == Material.AIR) {
			pendingItemInMainHand = Material.AIR;
			pendingItemData = 0;
			return;
		}
		Material material = itemStack.getType();
		boolean damageable = legacyDataValues && material.getMaxDurability() > 0;
		observeHeldItem(
			material, itemStack.getDurability(), damageable, legacyDataValues
		);
	}

	void observeHeldItem(
		Material material, short data, boolean damageable, boolean legacyDataValues
	) {
		pendingItemInMainHand = material;
		pendingItemData = legacyDataValues && !damageable ? data : 0;
	}

	public void reset() {
		minimumTicksSinceLastAttack = 0;
	}

	public boolean definitelyCharged(double attackSpeed) {
		if (!Double.isFinite(attackSpeed) || attackSpeed <= 0.0D) {
			return false;
		}
		float attackDelay = (float) (1.0D / attackSpeed * 20.0D);
		float attackStrength = Math.min(
			((float) minimumTicksSinceLastAttack + 0.5F) / attackDelay,
			1.0F
		);
		return attackStrength > 0.9F;
	}
}

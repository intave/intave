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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.player.CombatVersion;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.player.attribute.AttributeModifier;

/** Tracks the cooldown advertised by the server, before protocol translation. */
public final class CombatMetadata {
  // At twenty attacks per second the charge period is at most one game tick.
  private static final double LEGACY_ATTACK_SPEED = 20.0D;
  private final CombatVersion initialVersion;
  private volatile CombatVersion version;

  public CombatMetadata(boolean supportsAttackSpeed) {
    initialVersion = supportsAttackSpeed ? CombatVersion.UNKNOWN : CombatVersion.LEGACY;
    version = initialVersion;
  }

  public CombatVersion version() {
    return version;
  }

  public void reset() {
    version = initialVersion;
  }

  public static boolean isAttackSpeed(String key) {
    if (key.startsWith("minecraft:")) {
      key = key.substring("minecraft:".length());
    }
    return "generic.attackSpeed".equals(key)
      || "generic.attack_speed".equals(key)
      || "attack_speed".equals(key);
  }

  public void receiveAttribute(Attribute attribute) {
    if (!isAttackSpeed(attribute.attributeKey())) {
      return;
    }
    double base = attribute.baseValue();
    for (AttributeModifier modifier : attribute.modifiers()) {
      if (modifier.operation().getId() == 0) {
        base += modifier.amount();
      }
    }
    double value = base;
    for (AttributeModifier modifier : attribute.modifiers()) {
      if (modifier.operation().getId() == 1) {
        value += base * modifier.amount();
      }
    }
    for (AttributeModifier modifier : attribute.modifiers()) {
      if (modifier.operation().getId() == 2) {
        value *= 1.0D + modifier.amount();
      }
    }
    // Never turn missing or malformed evidence into a modern-combat assertion.
    // Zero is valid: it prevents the attack from charging, rather than disabling cooldown.
    version = !Double.isFinite(value) || value < 0.0D ? CombatVersion.UNKNOWN
      : value >= LEGACY_ATTACK_SPEED ? CombatVersion.LEGACY : CombatVersion.MODERN;
  }
}

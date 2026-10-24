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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class CombatMetadataTest {
  @Test
  void waitsForEvidenceOnModernServers() {
    CombatMetadata combat = new CombatMetadata(true);
    assertEquals(CombatVersion.UNKNOWN, combat.version());
    combat.receiveAttribute(attribute("generic.movementSpeed", 0.1D));
    assertEquals(CombatVersion.UNKNOWN, combat.version());
    combat.receiveAttribute(attribute("generic.attackSpeed", 4.0D));
    assertEquals(CombatVersion.MODERN, combat.version());
  }

  @Test
  void recognizesAttackSpeedAcrossAttributeRenames() {
    for (String key : Arrays.asList("generic.attackSpeed", "generic.attack_speed", "attack_speed",
      "minecraft:generic.attackSpeed", "minecraft:generic.attack_speed", "minecraft:attack_speed")) {
      CombatMetadata combat = new CombatMetadata(true);
      combat.receiveAttribute(attribute(key, 40.0D));
      assertEquals(CombatVersion.LEGACY, combat.version(), key);
    }
    assertFalse(CombatMetadata.isAttackSpeed("other:attack_speed"));
  }

  @Test
  void followsOldCombatMechanicsAndRestoredVanillaAttributes() {
    CombatMetadata combat = new CombatMetadata(true);
    AttributeModifier sword = modifier(0, -2.4D);
    combat.receiveAttribute(attribute("attack_speed", 4.0D, sword));
    assertEquals(CombatVersion.MODERN, combat.version());
    combat.receiveAttribute(attribute("attack_speed", 40.0D, sword));
    assertEquals(CombatVersion.LEGACY, combat.version());
    combat.receiveAttribute(attribute("attack_speed", 4.0D, sword));
    assertEquals(CombatVersion.MODERN, combat.version());
  }

  @Test
  void appliesAllModifierOperationsInGameOrder() {
    CombatMetadata combat = new CombatMetadata(true);
    // (4 + 2) * (1 + 1) * (1 + 1) = 24, independent of iteration order.
    combat.receiveAttribute(attribute("attack_speed", 4.0D,
      modifier(2, 1.0D), modifier(1, 1.0D), modifier(0, 2.0D)));
    assertEquals(CombatVersion.LEGACY, combat.version());
    // A high base alone must not imply that the effective cooldown is disabled.
    combat.receiveAttribute(attribute("attack_speed", 40.0D, modifier(2, -0.9D)));
    assertEquals(CombatVersion.MODERN, combat.version());
  }

  @Test
  void classifiesOneTickBoundaryAndZeroChargeSpeed() {
    CombatMetadata combat = new CombatMetadata(true);
    combat.receiveAttribute(attribute("attack_speed", Math.nextDown(20.0D)));
    assertEquals(CombatVersion.MODERN, combat.version());
    combat.receiveAttribute(attribute("attack_speed", 20.0D));
    assertEquals(CombatVersion.LEGACY, combat.version());
    combat.receiveAttribute(attribute("attack_speed", 0.0D));
    assertEquals(CombatVersion.MODERN, combat.version());
  }

  @Test
  void invalidEvidenceClearsPreviousClassification() {
    CombatMetadata combat = new CombatMetadata(true);
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0D}) {
      combat.receiveAttribute(attribute("attack_speed", 4.0D));
      combat.receiveAttribute(attribute("attack_speed", invalid));
      assertEquals(CombatVersion.UNKNOWN, combat.version());
    }
  }

  @Test
  void respawnRequiresFreshEvidenceOnModernServers() {
    CombatMetadata combat = new CombatMetadata(true);
    combat.receiveAttribute(attribute("attack_speed", 40.0D));
    combat.reset();
    assertEquals(CombatVersion.UNKNOWN, combat.version());
    combat.receiveAttribute(attribute("attack_speed", 4.0D));
    assertEquals(CombatVersion.MODERN, combat.version());
  }

  @Test
  void protocolsWithoutAttackSpeedUseLegacyCooldown() {
    CombatMetadata combat = new CombatMetadata(false);
    assertEquals(CombatVersion.LEGACY, combat.version());
    combat.reset();
    assertEquals(CombatVersion.LEGACY, combat.version());
  }

  private static Attribute attribute(String key, double base, AttributeModifier... modifiers) {
    return Attribute.newBuilder().withAttributeKey(key).withBaseValue(base)
      .withAttributeModifiers(new HashSet<>(Arrays.asList(modifiers))).build();
  }

  private static AttributeModifier modifier(int operation, double amount) {
    return new AttributeModifier(null, UUID.randomUUID(), "test",
      AttributeModifier.Operation.fromId(operation), amount);
  }
}

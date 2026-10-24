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

package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.player.attribute.Attribute;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class LiveCombatEnvironmentTest {
  @AfterEach
  void resetVersion() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void clientProtocolDoesNotOverrideServerAttributeEvidence() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    for (int protocol : new int[] {ProtocolMetadata.VER_1_8, ProtocolMetadata.VER_1_21_4}) {
      User user = UserFactory.createFallback();
      user.meta().protocol().setProtocolVersion(protocol);
      LiveEnvironment environment = new LiveEnvironment(user);
      assertCombat(environment, false, false);
      user.meta().combat().receiveAttribute(attackSpeed(4.0D));
      assertCombat(environment, true, true);
      Map<String, Boolean> before = environment.properties();
      user.meta().combat().receiveAttribute(attackSpeed(40.0D));
      assertCombat(environment, true, false);
      assertEquals(true, before.get("modernCombat"));
      user.meta().combat().reset();
      assertCombat(environment, false, false);
    }
  }

  @Test
  void modernClientOnLegacyServerIsNotMarkedModern() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
    User user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_1_21_4);
    assertCombat(new LiveEnvironment(user), true, false);
  }

  private static Attribute attackSpeed(double base) {
    return Attribute.newBuilder().withAttributeKey("attack_speed").withBaseValue(base).build();
  }

  private static void assertCombat(LiveEnvironment environment, boolean known, boolean modern) {
    assertEquals(known, environment.property("combatVersionKnown"));
    assertEquals(modern, environment.property("modernCombat"));
    assertEquals(known, environment.properties().get("combatVersionKnown"));
    assertEquals(modern, environment.properties().get("modernCombat"));
  }
}

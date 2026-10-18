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

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtocolMetadataItemUseTest {
  @BeforeAll
  static void setUpVersion() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
  }

  @Test
  void sustainedItemUseOnlyPreservesExistingSprintFrom114() {
    assertFalse(protocol(ProtocolMetadata.VER_1_13_2).sprintWhenHandActive());
    assertTrue(protocol(ProtocolMetadata.VER_1_14).sprintWhenHandActive());

    ProtocolMetadata modern = protocol(ProtocolMetadata.VER_1_21_2);
    assertFalse(modern.sprintCompatibleWithHandActive(false, false, false));
    assertTrue(modern.sprintCompatibleWithHandActive(true, false, false));
  }

  @Test
  void version1214OnlyPreservesItemUseSprintUnderwater() {
    ProtocolMetadata protocol = protocol(ProtocolMetadata.VER_1_21_4);

    assertFalse(protocol.sprintWhenHandActive());
    assertFalse(protocol.sprintCompatibleWithHandActive(true, false, false));
    assertTrue(protocol.sprintCompatibleWithHandActive(true, true, false));
    assertFalse(protocol.sprintCompatibleWithHandActive(false, true, false));
    assertTrue(protocol(ProtocolMetadata.VER_1_21_5).sprintWhenHandActive());
  }

  @Test
  void useEffectsCanSprintAllowsStartingSprintFrom12111() {
    assertFalse(protocol(ProtocolMetadata.VER_1_21_9)
      .sprintCompatibleWithHandActive(false, false, true));
    assertTrue(protocol(ProtocolMetadata.VER_1_21_11)
      .sprintCompatibleWithHandActive(false, false, true));
  }

  private static ProtocolMetadata protocol(int version) {
    return new ProtocolMetadata(null, version);
  }
}

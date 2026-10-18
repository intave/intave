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

package de.jpx3.intave.packet.reader;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class EntityMetadataReaderTest {
  @Test
  void resolvesLivingFlagsAcrossMetadataLayoutChanges() {
    MinecraftVersion original = MinecraftVersion.current();
    try {
      assertIndex(MinecraftVersions.VER1_9_0, 5);
      assertIndex(MinecraftVersions.VER1_10_0, 6);
      assertIndex(MinecraftVersions.VER1_14_0, 7);
      assertIndex(MinecraftVersions.VER1_17_0, 8);
      assertIndex(MinecraftVersions.VER26_3, 8);
    } finally {
      MinecraftVersion.setCurrent(original);
    }
  }

  private static void assertIndex(MinecraftVersion version, int expected) {
    MinecraftVersion.setCurrent(version);
    assertEquals(expected, EntityMetadataReader.livingFlagsIndex());
  }
}

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

package de.jpx3.intave.packet.plib.reader;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockDigReaderTest {
  private BlockDigReader reader;

  @BeforeEach
  void setUp() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    reader = new BlockDigReader();
  }

  @Test
  void acceptsVanillaItemUseReleaseCoordinates() {
    assertTrue(reader.isCanonicalItemUseRelease(
      BlockPosition.ORIGIN, Direction.DOWN
    ));
  }

  @Test
  void rejectsSpoofedItemUseReleaseCoordinatesOrDirection() {
    assertFalse(reader.isCanonicalItemUseRelease(
      new BlockPosition(1, 64, 1), Direction.DOWN
    ));
    assertFalse(reader.isCanonicalItemUseRelease(
      BlockPosition.ORIGIN, Direction.UP
    ));
    assertFalse(reader.isCanonicalItemUseRelease(null, null));
  }
}

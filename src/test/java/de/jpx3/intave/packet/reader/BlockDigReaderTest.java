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

import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockDigReaderTest {
  @Test
  void acceptsVanillaItemUseReleaseCoordinates() {
    assertTrue(BlockDigReader.isCanonicalItemUseRelease(
      BlockPosition.ORIGIN, Direction.DOWN
    ));
  }

  @Test
  void rejectsSpoofedItemUseReleaseCoordinatesOrDirection() {
    assertFalse(BlockDigReader.isCanonicalItemUseRelease(
      new BlockPosition(1, 64, 1), Direction.DOWN
    ));
    assertFalse(BlockDigReader.isCanonicalItemUseRelease(
      BlockPosition.ORIGIN, Direction.UP
    ));
    assertFalse(BlockDigReader.isCanonicalItemUseRelease(null, null));
  }
}

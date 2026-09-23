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

package de.jpx3.intave.player;

import com.comphenix.protocol.wrappers.EnumWrappers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ItemReleaseValidationTest {
  @Test
  void acceptsVanillaDownRelease() {
    assertFalse(ItemReleaseValidation.isSpoofedRelease(EnumWrappers.Direction.DOWN));
  }

  @Test
  void rejectsEveryOtherFacing() {
    for (EnumWrappers.Direction face : EnumWrappers.Direction.values()) {
      if (face == EnumWrappers.Direction.DOWN) {
        continue;
      }
      assertTrue(
        ItemReleaseValidation.isSpoofedRelease(face),
        "expected spoofed release for facing " + face
      );
    }
  }

  @Test
  void acceptsMissingFacingWithoutProof() {
    assertFalse(ItemReleaseValidation.isSpoofedRelease(null));
  }
}

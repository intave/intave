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

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.share.EntityAnimation.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationReaderTest {
  private final AnimationReader reader = new AnimationReader();

  @Test
  void decodesRenumbered263Animations() {
    assertEquals(WAKEUP, reader.decodeAnimation(0, true));
    assertEquals(CRIT, reader.decodeAnimation(1, true));
    assertEquals(CRIT_MAGIC, reader.decodeAnimation(2, true));
    assertNull(reader.decodeAnimation(3, true));
  }

  @Test
  void retainsLegacySwingAndWakeupIds() {
    assertEquals(SWING, reader.decodeAnimation(0, false));
    assertEquals(WAKEUP, reader.decodeAnimation(2, false));
    assertEquals(SWING_OFFHAND, reader.decodeAnimation(3, false));
    assertEquals(CRIT, reader.decodeAnimation(4, false));
    assertEquals(CRIT_MAGIC, reader.decodeAnimation(5, false));
    assertNull(reader.decodeAnimation(-1, false));
    assertNull(reader.decodeAnimation(6, false));
  }
}

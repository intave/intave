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

import de.jpx3.intave.packet.plib.reader.AnimationReader;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.share.EntityAnimation.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnimationReaderTest {
  @Test
  void decodesRenumbered263Animations() {
    assertEquals(WAKEUP, AnimationReader.decodeAnimation(0, true));
    assertEquals(CRIT, AnimationReader.decodeAnimation(1, true));
    assertEquals(CRIT_MAGIC, AnimationReader.decodeAnimation(2, true));
    assertNull(AnimationReader.decodeAnimation(3, true));
  }

  @Test
  void retainsLegacySwingAndWakeupIds() {
    assertEquals(SWING, AnimationReader.decodeAnimation(0, false));
    assertEquals(WAKEUP, AnimationReader.decodeAnimation(2, false));
    assertEquals(SWING_OFFHAND, AnimationReader.decodeAnimation(3, false));
    assertEquals(CRIT, AnimationReader.decodeAnimation(4, false));
    assertEquals(CRIT_MAGIC, AnimationReader.decodeAnimation(5, false));
    assertNull(AnimationReader.decodeAnimation(-1, false));
    assertNull(AnimationReader.decodeAnimation(6, false));
  }
}

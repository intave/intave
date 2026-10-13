package de.jpx3.intave.packet.reader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static de.jpx3.intave.share.EntityAnimation.*;

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

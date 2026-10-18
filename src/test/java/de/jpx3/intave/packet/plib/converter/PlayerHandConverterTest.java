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

package de.jpx3.intave.packet.plib.converter;

import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.share.PlayerHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class PlayerHandConverterTest {
  @Test
  void preservesBothHandsAcrossThePacketBoundary() {
    assertEquals(PlayerHand.MAIN_HAND, PlayerHandConverter.fromProtocolLib(EnumWrappers.Hand.MAIN_HAND));
    assertEquals(PlayerHand.OFF_HAND, PlayerHandConverter.fromProtocolLib(EnumWrappers.Hand.OFF_HAND));
    assertEquals(EnumWrappers.Hand.MAIN_HAND, PlayerHandConverter.toProtocolLib(PlayerHand.MAIN_HAND));
    assertEquals(EnumWrappers.Hand.OFF_HAND, PlayerHandConverter.toProtocolLib(PlayerHand.OFF_HAND));
  }

  @Test
  void preservesAbsentHandValues() {
    assertNull(PlayerHandConverter.fromProtocolLib(null));
    assertNull(PlayerHandConverter.toProtocolLib(null));
  }
}

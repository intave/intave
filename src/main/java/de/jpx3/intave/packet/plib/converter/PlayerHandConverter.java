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
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.PlayerHand;

/** Keeps ProtocolLib hand values at the packet boundary. */
public final class PlayerHandConverter {
  private PlayerHandConverter() {
  }

  public static @Nullable PlayerHand fromProtocolLib(@Nullable EnumWrappers.Hand hand) {
    return hand == null ? null : PlayerHand.valueOf(hand.name());
  }

  public static @Nullable EnumWrappers.Hand toProtocolLib(@Nullable PlayerHand hand) {
    return hand == null ? null : EnumWrappers.Hand.valueOf(hand.name());
  }
}

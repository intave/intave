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

import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.PlayerHand;

public final class ArmAnimationReader extends AbstractPacketReader {
  public PlayerHand hand() {
    if (MinecraftVersions.VER1_9_0.below()) {
      return PlayerHand.MAIN_HAND;
    }
    EnumWrappers.Hand hand = packet().getHands().readSafely(0);
    return hand == null ? null : PlayerHand.valueOf(hand.name());
  }
}

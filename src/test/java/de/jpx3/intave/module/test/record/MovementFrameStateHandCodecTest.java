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

package de.jpx3.intave.module.test.record;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.PlayerHand;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MovementFrameStateHandCodecTest {
  // The recorded field used -1 for absent, 0 for main hand, and 1 for offhand.
  private static final StreamCodec<ByteBuf, ByteBuf, Integer> LEGACY_HAND_FIELD =
    ByteBufStreamCodecs.<Integer>smartCodec(
      schema -> schema.field("activeHand", ByteBufStreamCodecs.INTEGER, value -> value),
      values -> values.integerValue("activeHand")
    );

  @Test
  void readsExistingRecordedHandValues() {
    assertLegacyDecodes(-1, null);
    assertLegacyDecodes(0, PlayerHand.MAIN_HAND);
    assertLegacyDecodes(1, PlayerHand.OFF_HAND);
  }

  @Test
  void keepsWrittenHandValuesCompatibleWithExistingRecordings() {
    assertLegacyEncodes(null, -1);
    assertLegacyEncodes(PlayerHand.MAIN_HAND, 0);
    assertLegacyEncodes(PlayerHand.OFF_HAND, 1);
  }

  private static void assertLegacyDecodes(int value, PlayerHand hand) {
    ByteBuf buffer = Unpooled.buffer();
    try {
      LEGACY_HAND_FIELD.encode(buffer, value);
      assertEquals(state(hand), MovementFrameState.InventoryState.STREAM_CODEC.decode(buffer));
      assertEquals(0, buffer.readableBytes());
    } finally {
      buffer.release();
    }
  }

  private static void assertLegacyEncodes(PlayerHand hand, int value) {
    ByteBuf buffer = Unpooled.buffer();
    try {
      MovementFrameState.InventoryState.STREAM_CODEC.encode(buffer, state(hand));
      assertEquals(value, LEGACY_HAND_FIELD.decode(buffer).intValue());
      assertEquals(0, buffer.readableBytes());
    } finally {
      buffer.release();
    }
  }

  private static MovementFrameState.InventoryState state(PlayerHand hand) {
    return new MovementFrameState.InventoryState(
      Collections.emptyList(), Collections.emptyList(), null,
      0, false, 0, 100, 0, false, Material.AIR,
      false, false, Material.AIR, false, false,
      hand, false, 0.2F
    );
  }
}

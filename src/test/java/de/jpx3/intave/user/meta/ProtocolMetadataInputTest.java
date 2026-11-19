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

package de.jpx3.intave.user.meta;

import de.jpx3.intave.adapter.MinecraftVersion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtocolMetadataInputTest {
  @Test
  void replayAvailabilityDoesNotDisableNativeInputsForOtherUsersOnLegacyServers() throws Exception {
    java.lang.reflect.Field version = MinecraftVersion.class.getDeclaredField("currentVersion");
    version.setAccessible(true);
    Object previousVersion = version.get(null);
    try {
      MinecraftVersion.setCurrent(new MinecraftVersion("1.8.8"));
      ProtocolMetadata live = new ProtocolMetadata(null, ProtocolMetadata.VER_1_21_2);
      ProtocolMetadata replay = new ProtocolMetadata(null, ProtocolMetadata.VER_26_3);
      ProtocolMetadata legacy = new ProtocolMetadata(null, ProtocolMetadata.VER_1_21);

      assertTrue(live.sendsInputs());
      assertTrue(replay.sendsInputs());
      assertFalse(legacy.sendsInputs());
      replay.setInputPacketsAvailable(false);
      assertFalse(replay.sendsInputs());
      assertTrue(live.sendsInputs());
      replay.setInputPacketsAvailable(true);
      assertTrue(replay.sendsInputs());
      legacy.setInputPacketsAvailable(true);
      assertFalse(legacy.sendsInputs());
    } finally {
      version.set(null, previousVersion);
    }
  }
}

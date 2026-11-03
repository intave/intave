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

import com.comphenix.protocol.wrappers.Converters;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class PlayerInfoRemoveReader extends AbstractPacketReader {
  public List<UUID> playersToRemove() {
    List<UUID> uuids = packet().getLists(Converters.passthrough(UUID.class)).readSafely(0);
    if (uuids == null) {
      return Collections.emptyList();
    }
    return uuids;
  }
}

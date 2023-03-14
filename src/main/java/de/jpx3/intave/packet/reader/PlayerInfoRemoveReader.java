package de.jpx3.intave.packet.reader;

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

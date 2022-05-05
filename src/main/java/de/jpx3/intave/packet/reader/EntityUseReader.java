package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.wrappers.EnumWrappers;

public final class EntityUseReader extends EntityReader {
  public EnumWrappers.EntityUseAction useAction() {
    EnumWrappers.EntityUseAction action = packet.getEntityUseActions().readSafely(0);
    if (action == null) {
      action = packet.getEnumEntityUseActions().read(0).getAction();
    }
    return action;
  }
}

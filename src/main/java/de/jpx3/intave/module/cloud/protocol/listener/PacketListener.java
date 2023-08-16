package de.jpx3.intave.module.cloud.protocol.listener;

import de.jpx3.intave.module.cloud.protocol.Packet;

public interface PacketListener {
  default void onAny(Packet<?> packet) {

  }
}

package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketContainer;

public interface PacketReader {
  void enter(PacketContainer packet);
  void release();
}

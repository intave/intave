package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketContainer;

public interface PacketReader {
  void flush(PacketContainer packet);
  void close();
}

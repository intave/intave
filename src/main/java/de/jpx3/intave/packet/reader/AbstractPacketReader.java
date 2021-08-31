package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.events.PacketContainer;

public abstract class AbstractPacketReader implements PacketReader {
  protected PacketContainer packet;

  @Override
  public void flush(PacketContainer packet) {
    if (this.packet != null) {
      throw new IllegalStateException("Missing reader flush of " + getClass());
    }
    this.packet = packet;
  }

  @Override
  public void close() {
    packet = null;
  }
}

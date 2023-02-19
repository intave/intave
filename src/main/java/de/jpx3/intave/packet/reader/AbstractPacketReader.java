package de.jpx3.intave.packet.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPacketReader implements PacketReader {
  private static final Map<PacketType, AtomicLong> MISSING_FLUSHES_BY_TYPE = new HashMap<>();

  private PacketContainer packet;

  @Override
  public void enter(PacketContainer packet) {
    if (this.packet != null) {
//      throw new IllegalStateException("Missing reader flush of " + getClass());
      MISSING_FLUSHES_BY_TYPE.computeIfAbsent(packet.getType(), packetType -> new AtomicLong()).incrementAndGet();
    }
    this.packet = packet;
  }

  @Override
  public void release() {
    packet = null;
  }

  public PacketContainer packet() {
    return packet;
  }
}

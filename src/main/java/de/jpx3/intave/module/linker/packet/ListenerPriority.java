package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.PacketListenerPriority;

public enum ListenerPriority {
  LOWEST(1),
  LOW(2),
  NORMAL(3),
  HIGH(4),
  HIGHEST(5),
  MONITOR(6);

  final int slot;

  ListenerPriority(int slot) {
    this.slot = slot;
  }

  public int slot() {
    return slot;
  }

  public PacketListenerPriority toPacketEventsPriority() {
    switch (this) {
      case LOWEST:
        return PacketListenerPriority.LOWEST;
      case LOW:
        return PacketListenerPriority.LOW;
      case NORMAL:
        return PacketListenerPriority.NORMAL;
      case HIGH:
        return PacketListenerPriority.HIGH;
      case HIGHEST:
        return PacketListenerPriority.HIGHEST;
      case MONITOR:
        return PacketListenerPriority.MONITOR;
    }
    return null;
  }
}

package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;

public interface PacketSubscriptionMethodExecutor {
  void invoke(PacketEventSubscriber subscriber, ProtocolPacketEvent event);
}

package de.jpx3.intave.module.linker.packet;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2020
 */

public interface PacketSubscriptionMethodExecutor {
  void invoke(PacketEventSubscriber subscriber, ProtocolPacketEvent event);
}
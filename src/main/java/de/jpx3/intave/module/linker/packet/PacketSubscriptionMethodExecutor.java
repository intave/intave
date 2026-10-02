package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.events.PacketEvent;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2020
 */

public interface PacketSubscriptionMethodExecutor {
  void invoke(PacketEventSubscriber subscriber, PacketEvent event);
}

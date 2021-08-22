package de.jpx3.intave.module.linker.packet;

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.NameIntrinsicallyImportant;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2020
 */

@NameIntrinsicallyImportant
public interface PacketSubscriptionMethodExecutor {
  void invoke(PacketEventSubscriber subscriber, PacketEvent event);
}
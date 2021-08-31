package de.jpx3.intave.module.linker.packet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PacketSubscription {
  ListenerPriority priority() default ListenerPriority.NORMAL;
  PrioritySlot prioritySlot() default PrioritySlot.INTERNAL;
  Engine engine() default Engine.SYNC_PROTOCOL;
  String identifier() default "no identifier assigned";
  PacketId.Client[] packetsIn() default {};
  PacketId.Server[] packetsOut() default {};
  boolean ignoreCancelled() default true;
}

/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.linker.packet;

import de.jpx3.intave.packet.contract.PacketType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PacketSubscription {
  ListenerPriority priority() default ListenerPriority.NORMAL;
  PrioritySlot prioritySlot() default PrioritySlot.INTERNAL;
  Engine engine() default Engine.PROTOCOLLIB;
  String identifier() default "no identifier assigned";
  PacketType.Client[] packetsIn() default {};
  PacketType.Server[] packetsOut() default {};
  boolean ignoreCancelled() default true;
  boolean debug() default false;
}

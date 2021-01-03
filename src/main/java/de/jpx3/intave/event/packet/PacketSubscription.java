package de.jpx3.intave.event.packet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2020
 */

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PacketSubscription {
  ListenerPriority priority() default ListenerPriority.FIFTH;
  String identifier() default "no identifier assigned";
  PacketDescriptor[] packets();
}
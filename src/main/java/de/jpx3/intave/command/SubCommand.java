package de.jpx3.intave.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {
  String[] selectors();
  String usage() default "invalid";
  String permission() default "none";
  String description() default "none";
}

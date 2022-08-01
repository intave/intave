package de.jpx3.intave.test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2022
 */

@Retention(RetentionPolicy.RUNTIME)
public @interface Test {
  String testCode() default "NOT SET";
  Severity severity() default Severity.INFO;
}

package de.jpx3.intave.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reserved classes are only present for partner servers
 */
@NameIntrinsicallyImportant
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Reserved {
}

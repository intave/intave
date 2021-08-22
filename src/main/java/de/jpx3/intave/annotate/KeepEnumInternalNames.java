package de.jpx3.intave.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@NameIntrinsicallyImportant
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface KeepEnumInternalNames {
}
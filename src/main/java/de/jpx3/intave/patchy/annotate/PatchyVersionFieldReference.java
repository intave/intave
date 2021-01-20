package de.jpx3.intave.patchy.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PatchyVersionFieldReference {
  String version();
  String owner();
  String name();
  String desc();
}

package de.jpx3.intave.patchy.annotate;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface PatchyCustomFieldTranslation {
  PatchyVersionFieldReference[] value();
  PatchyUnknownVersionPolicy unknownVersionPolicy() default PatchyUnknownVersionPolicy.USE_NEXT_LOWER;
}

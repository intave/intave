package de.jpx3.intave.klass.locate;

import de.jpx3.intave.klass.Lookup;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

final class FieldLocation extends Location {
  private static final Reference<Field> EMPTY_FIELD_REFERENCE = new WeakReference<>(null);
  private final String classKey;
  private final String target;
  private Reference<Field> fieldCache = EMPTY_FIELD_REFERENCE;

  public FieldLocation(String classKey, String key, IntegerMatcher versionMatcher, String target) {
    super(key, versionMatcher);
    this.classKey = classKey;
    this.target = target;
  }

  public Field access() {
    Field field = fieldCache.get();
    if (field == null) {
      field = compile();
      fieldCache = new WeakReference<>(field);
    }
    return field;
  }

  private Field compile() {
    try {
      Field declaredField = Lookup.serverClass(classKey).getDeclaredField(target);
      if (!declaredField.isAccessible()) {
        declaredField.setAccessible(true);
      }
      return declaredField;
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  public String targetName() {
    return target;
  }

  public String classKey() {
    return classKey;
  }

  @Override
  public String toString() {
    return "{" + classKey + "/" + key() + " -> " + target + " @" + versionMatcher() + "}";
  }

  public static FieldLocation defaultFor(String classKey, String fieldKey) {
    return new FieldLocation(classKey, fieldKey, IntegerMatcher.any(), fieldKey);
  }
}

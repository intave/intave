package de.jpx3.intave.block.variant;

public interface BlockVariant {
  Comparable<?> propertyOf(String name);

  <T extends Enum<T>> T enumProperty(Class<T> clazz, String name);
}

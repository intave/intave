package de.jpx3.intave.block.variant;

final class EmptyBlockVariant implements BlockVariant {
  @Override
  public Comparable<?> propertyOf(String name) {
    return null;
  }

  @Override
  public <T extends Enum<T>> T enumProperty(Class<T> clazz, String name) {
    return null;
  }
}

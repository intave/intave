package de.jpx3.intave.block.variant;

public interface BlockVariant {
  <T> T propertyOf(String name);

  <T extends Enum<T>> T enumProperty(Class<T> klass, String name);

  int index();

  void dumpStates();
}

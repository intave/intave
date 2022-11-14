package de.jpx3.intave.block.variant;

import java.util.Set;

public interface BlockVariant {
  Set<String> propertyNames();

  <T> T propertyOf(String name);

  <T extends Enum<T>> T enumProperty(Class<T> klass, String name);

  int index();

  void dumpStates();
}

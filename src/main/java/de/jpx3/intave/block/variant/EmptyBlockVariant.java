package de.jpx3.intave.block.variant;

import java.util.Collections;
import java.util.Set;

final class EmptyBlockVariant implements BlockVariant {
  @Override
  public Set<String> propertyNames() {
    return Collections.emptySet();
  }

  @Override
  public <T> T propertyOf(String name) {
    return null;
  }

  @Override
  public <T extends Enum<T>> T enumProperty(Class<T> klass, String name) {
    return null;
  }

  @Override
  public int index() {
    return 0;
  }

  @Override
  public void dumpStates() {
  }
}

package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Optional;

public final class BooleanSetting extends NamedSetting<Boolean> {
  private final ImmutableSet<Boolean> values = ImmutableSet.of(true, false);

  public BooleanSetting(String name) {
    super(name, Boolean.class);
  }

  @Override
  public Collection<Boolean> values() {
    return values;
  }

  @Override
  public Optional<Boolean> findByName(String name) {
    return !"true".equals(name) && !"false".equals(name) ? Optional.empty() : Optional.of(Boolean.valueOf(name));
  }
}

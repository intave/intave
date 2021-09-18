package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;

import java.util.*;

public class EnumSetting<T extends Enum<T>> extends NamedSetting<T> {
  private final ImmutableSet<T> values;
  private final Map<String, T> enumMap = new HashMap<>();

  public EnumSetting(String name, Class<T> clazz, Collection<T> values) {
    super(name, clazz);
    this.values = ImmutableSet.copyOf(values);
    for (T value : values) {
      String valueName = value.name().toLowerCase(Locale.ROOT);
      enumMap.put(valueName, value);
    }
  }

  @Override
  public Collection<T> values() {
    return values;
  }

  @Override
  public Optional<T> findByName(String name) {
    return Optional.ofNullable(this.enumMap.get(name));
  }
}

package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;

import java.util.*;

public final class UnknownEnumSetting extends NamedSetting<Integer> {
  private final ImmutableSet<Integer> values;
  private final Map<String, Integer> mapped = new HashMap<>();
  private final Map<Integer, String> reverseMapped = new HashMap<>();

  public UnknownEnumSetting(String name, Class<?> owner, Collection<?> values) {
    super(name, Integer.TYPE);
    if (!owner.isEnum()) {
      throw new IllegalStateException("Not an enum");
    }
    for (Object value : values) {
      String key = value.toString().toLowerCase(Locale.ROOT);
      int ordinal = ((Enum<?>) value).ordinal();
      mapped.put(key, ordinal);
      reverseMapped.put(ordinal, key);
    }
    ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
    values.stream().mapToInt(value -> ((Enum<?>) value).ordinal()).forEach(builder::add);
    this.values = builder.build();
  }

  public <K extends Enum<K>> K enumType(Class<K> enumClass, int index) {
    return Enum.valueOf(enumClass, reverseMapped.get(index));
  }

  @Override
  public Collection<Integer> values() {
    return values;
  }

  @Override
  public Optional<Integer> findByName(String name) {
    return Optional.ofNullable(mapped.get(name.toLowerCase(Locale.ROOT)));
  }
}

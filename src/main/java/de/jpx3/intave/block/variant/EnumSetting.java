package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;

import java.util.*;

final class EnumSetting extends NamedSetting<Integer> {
  private final ImmutableSet<Integer> values;
  private final Class<?> owner;
  private final Map<String, Integer> enumNameToIndex = new HashMap<>();
  private final Map<Integer, String> enumIndexToName = new HashMap<>();

  public EnumSetting(String name, Class<?> owner, Collection<?> values) {
    super(name, Integer.TYPE);
    if (!owner.isEnum()) {
      throw new IllegalStateException("Not an enum");
    }
    this.owner = owner;
    for (Object value : values) {
      String key = value.toString().toLowerCase(Locale.ROOT);
      int ordinal = ((Enum<?>) value).ordinal();
      enumNameToIndex.put(key, ordinal);
      enumIndexToName.put(ordinal, key);
    }
    ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
    values.stream().mapToInt(value -> ((Enum<?>) value).ordinal()).forEach(builder::add);
    this.values = builder.build();
  }

  public <K extends Enum<K>> K enumType(Class<K> enumClass, int index) {
    String enumName = enumIndexToName.get(index);
    if (enumName == null) {
      System.out.println("Unknown enum index " + index + " in " + enumIndexToName + " for " + enumClass + ", owned by " + owner);
      Thread.dumpStack();
      return enumClass.getEnumConstants()[0];
    }
    return Enum.valueOf(enumClass, enumName.toUpperCase(Locale.ROOT));
  }

  @Override
  public int indexOf(Integer value) {
    return value;
  }

  @Override
  public Collection<Integer> values() {
    return values;
  }

  @Override
  public Optional<Integer> findByName(String name) {
    return Optional.ofNullable(enumNameToIndex.get(name.toLowerCase(Locale.ROOT)));
  }
}

package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.Hash;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class IntegerSetting extends NamedSetting<Integer> {
  private final Set<Integer> values;
  private final Map<Integer, Integer> numberToIndexLookup = new HashMap<>();

  IntegerSetting(String name, int min, int max) {
    super(name, Integer.TYPE);
    List<Integer> numbers = IntStream.rangeClosed(min, max).boxed().collect(Collectors.toList());
    this.values = ImmutableSet.copyOf(numbers);
    IntStream.range(0, numbers.size()).forEach(i -> numberToIndexLookup.put(numbers.get(i), i));
  }

  @Override
  public Collection<Integer> values() {
    return values;
  }

  @Override
  public int indexOf(Integer value) {
    return numberToIndexLookup.getOrDefault(value, -1);
  }

  @Override
  public Optional<Integer> findByName(String name) {
    try {
      Integer integer = Integer.valueOf(name);
      return values.contains(integer) ? Optional.of(integer) : Optional.empty();
    } catch (NumberFormatException var3) {
      return Optional.empty();
    }
  }
}

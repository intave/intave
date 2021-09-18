package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntegerSetting extends NamedSetting<Integer> {
  private final ImmutableSet<Integer> values;
  private final int from, to;

  public IntegerSetting(String name, int min, int max) {
    super(name, Integer.TYPE);
    this.from = min;
    this.to = max;

    if (min < 0) {
      throw new IllegalArgumentException("Min value of " + name + " must be 0 or greater");
    } else if (max <= min) {
      throw new IllegalArgumentException("Max value of " + name + " must be greater than min (" + min + ")");
    } else {
      this.values = ImmutableSet.copyOf(IntStream.rangeClosed(min, max).boxed().collect(Collectors.toSet()));
    }
  }

  @Override
  public Collection<Integer> values() {
    return values;
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

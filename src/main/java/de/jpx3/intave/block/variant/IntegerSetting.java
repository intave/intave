package de.jpx3.intave.block.variant;

import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IntegerSetting extends NamedSetting<Integer> {
  private final Set<Integer> values;

  public IntegerSetting(String name, int min, int max) {
    super(name, Integer.TYPE);
    this.values = ImmutableSet.copyOf(
      IntStream.rangeClosed(min, max).boxed().collect(Collectors.toSet())
    );
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

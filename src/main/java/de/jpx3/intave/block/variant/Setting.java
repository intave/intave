package de.jpx3.intave.block.variant;

import java.util.Collection;
import java.util.Optional;

/**
 * Class generated using IntelliJ IDEA
 * Created by Richard Strunk 2021
 */

public interface Setting<T extends Comparable<T>> {
  String name();

  Collection<T> values();

  Class<T> type();

  Optional<T> findByName(String name);
}

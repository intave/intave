package de.jpx3.intave.block.variant;

import java.util.Objects;

public abstract class NamedSetting<T extends Comparable<T>> implements Setting<T> {
  private final Class<T> clazz;
  private final String name;
  private Integer hashCode;

  protected NamedSetting(String name, Class<T> clazz) {
    this.name = name;
    this.clazz = clazz;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Class<T> type() {
    return clazz;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    NamedSetting<?> that = (NamedSetting<?>) o;

    if (!Objects.equals(name, that.name)) return false;
    return Objects.equals(hashCode, that.hashCode);
  }

  @Override
  public int hashCode() {
    if (hashCode == null) {
      hashCode = internalHashCode();
    }
    return hashCode;
  }

  private int internalHashCode() {
    return 31 * clazz.hashCode() + name.hashCode();
  }
}

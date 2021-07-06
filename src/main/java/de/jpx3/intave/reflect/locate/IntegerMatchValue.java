package de.jpx3.intave.reflect.locate;

public final class IntegerMatchValue extends IntegerMatcher {
  private final int value;

  public IntegerMatchValue(int value) {
    this.value = value;
  }

  @Override
  public boolean matches(int integer) {
    return integer == value;
  }

  @Override
  public String toString() {
    return "({value} == " + value + ")";
  }
}

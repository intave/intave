package de.jpx3.intave.reflect.locate;

public final class IntegerMatchRange extends IntegerMatcher {
  private final int start, end;

  public IntegerMatchRange(int start, int end) {
    this.start = Math.min(start, end);
    this.end = Math.max(start, end);
  }

  @Override
  public boolean matches(int integer) {
    return start <= integer && integer <= end;
  }

  @Override
  public String toString() {
    return "(" + start + " <= {value} <= " + end + ")";
  }
}

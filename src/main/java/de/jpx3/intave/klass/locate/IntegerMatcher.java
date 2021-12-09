package de.jpx3.intave.klass.locate;

import java.util.function.BinaryOperator;

public abstract class IntegerMatcher {
  public abstract boolean matches(int integer);

  public static IntegerMatcher any() {
    return between(Integer.MIN_VALUE, Integer.MAX_VALUE);
  }

  public static IntegerMatcher between(int from, int to) {
    return new IntegerMatchRange(from, to);
  }

  public static IntegerMatcher exact(int val) {
    return new IntegerMatchValue(val);
  }

  public IntegerMatcher or(IntegerMatcher matcher) {
    return merge(MergeOperation.OR, this, matcher);
  }

  public IntegerMatcher and(IntegerMatcher matcher) {
    return merge(MergeOperation.AND, this, matcher);
  }

  private static IntegerMatcher merge(MergeOperation operation, IntegerMatcher... matchers) {
    return new IntegerMatcher() {
      @Override
      public boolean matches(int integer) {
        if (matchers.length == 1) {
          return matchers[0].matches(integer);
        }
        BinaryOperator<Boolean> applier = operation.applier();
        boolean mem = applier.apply(matchers[0].matches(integer), matchers[1].matches(integer));
        for (int i = 1; i < matchers.length; i++) {
          mem = applier.apply(mem, matchers[i].matches(integer));
        }
        return mem;
      }

      @Override
      public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < matchers.length; i++) {
          IntegerMatcher matcher = matchers[i];
          boolean last = i + 1 == matchers.length;
          stringBuilder.append(matcher.toString()).append(" ");
          if (!last) {
            stringBuilder.append(operation.output).append(" ");
          }
        }
        return "(" + stringBuilder + ")";
      }
    };
  }

  public enum MergeOperation {
    AND((bool0, bool1) -> bool0 && bool1, "&&"),
    OR((bool0, bool1) -> bool0 || bool1, "||");

    final BinaryOperator<Boolean> function;
    final String output;

    MergeOperation(BinaryOperator<Boolean> function, String output) {
      this.function = function;
      this.output = output;
    }

    public BinaryOperator<Boolean> applier() {
      return function;
    }

    public String output() {
      return output;
    }
  }
}

package de.jpx3.intave.clazz.locate;

import java.util.function.BiFunction;

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

  public static IntegerMatcher mergeOr(IntegerMatcher... matchers) {
    return merge(MergeOperation.OR, matchers);
  }

  public static IntegerMatcher mergeAnd(IntegerMatcher... matchers) {
    return merge(MergeOperation.AND, matchers);
  }

  private static IntegerMatcher merge(MergeOperation operation, IntegerMatcher... matchers) {
    return new IntegerMatcher() {
      @Override
      public boolean matches(int integer) {
        if (matchers.length == 1) {
          return matchers[0].matches(integer);
        }
        BiFunction<Boolean, Boolean, Boolean> applierFunction = operation.applierFunction();
        boolean mem = applierFunction.apply(matchers[0].matches(integer), matchers[1].matches(integer));
        for (int i = 1; i < matchers.length; i++) {
          mem = applierFunction.apply(mem, matchers[i].matches(integer));
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

    BiFunction<Boolean, Boolean, Boolean> function;
    String output;

    MergeOperation(BiFunction<Boolean, Boolean, Boolean> function, String output) {
      this.function = function;
      this.output = output;
    }

    public BiFunction<Boolean, Boolean, Boolean> applierFunction() {
      return function;
    }

    public String output() {
      return output;
    }
  }
}

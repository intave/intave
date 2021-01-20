package de.jpx3.intave.tools;

import java.util.List;

public final class RotationMathHelper {
  public static double averageOf(List<? extends Number> data) {
    double sum = 0;
    for (Number element : data) {
      sum += element.doubleValue();
    }
    if (sum == 0) {
      return 0;
    }
    return sum / data.size();
  }

  public static double calculateStandardDeviation(List<? extends Number> sd) {
    double sum = 0, newSum = 0;
    for (Number v : sd) {
      sum = sum + v.doubleValue();
    }
    double mean = sum / sd.size();
    for (Number v : sd) {
      newSum = newSum + (v.doubleValue() - mean) * (v.doubleValue() - mean);
    }
    return Math.sqrt(newSum / sd.size());
  }

  public static double gcdExact(double a, double b) {
    double r;
    while ((r = a % b) > 0) {
      a = b;
      b = r;
    }
    return b;
  }

  public static double gcd(double a, double b) {
    double r;
    double min = 0;
    while ((r = a % b) > min) {
      a = b;
      b = r;
      min = Math.max(a, b) * 1e-3;
    }
    return b;
  }

  public static float resolveSensitivity(float gcd) {
    return (float) ((Math.cbrt(gcd / 8.0f) - 0.2f) / 0.6f * 2f);
  }
}
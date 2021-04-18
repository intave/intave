package de.jpx3.intave.tools;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class MathHelper {
  public static String formatDouble(double value, int digits) {
    if (Double.isNaN(value)) {
      return "NaN";
    }
    if (Double.isInfinite(value)) {
      return "Infinite";
    }
    return new BigDecimal(value).setScale(digits, RoundingMode.HALF_UP).toPlainString();
  }

  public static double map(double currentValue, double min, double max, double min2, double max2) {
    return (currentValue - min) / (max - min) * (max2 - min2) + min2;
  }

  /**
   * Long version of floor_double
   */
  public static long floor_double_long(double value)
  {
    long i = (long)value;
    return value < (double)i ? i - 1L : i;
  }

  public static double minmax(double min, double val, double max) {
    return Math.max(min, Math.min(val, max));
  }

  public static int minmax(int min, int val, int map) {
    return Math.max(min, Math.min(val, map));
  }

  public static long minmax(long min, long val, long max) {
    return Math.max(min, Math.min(val, max));
  }

  public static double diff(double a, double b) {
    return Math.abs( a- b);
  }

  public static double maximumIn(List<? extends Number> numbers) {
    double maximum = 0;
    for (Number number : numbers) {
      maximum = Math.max(maximum, number.doubleValue());
    }
    return maximum;
  }

  public static double minimumIn(List<? extends Number> numbers) {
    double minimum = 1000;
    for (Number number : numbers) {
      minimum = Math.min(minimum, number.doubleValue());
    }
    return minimum;
  }

  public static float distanceInDegrees(float alpha, float beta) {
    float phi = Math.abs(beta - alpha) % 360;
    return phi > 180 ? 360 - phi : phi;
  }

  public static String formatPosition(Location location) {
    return formatPosition(location.getX(), location.getY(),location.getZ());
  }

  public static String formatMotion(Vector vector) {
    return formatPosition(vector.getX(), vector.getY(),vector.getZ());
  }

  public static String formatPosition(double x, double y, double z) {
    return formatDouble(x, 3) + ", " + formatDouble(y, 4) + ", " + formatDouble(z, 3);
  }

  public static String formatPositionAsInt(double x, double y, double z) {
    return (int) x + "," + (int) y + "," + (int) z;
  }

  public static double resolveDistance(double differenceX, double differenceY, double differenceZ) {
    return Math.sqrt(differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ);
  }

  public static double resolveDistance(
    double x1, double y1, double z1,
    double x2, double y2, double z2
  ) {
    return resolveDistance(x1 - x2, y1 - y2, z1 - z2);
  }

  public static double resolveHorizontalDistance(double x1, double z1, double x2, double z2) {
    return Math.hypot(x1 - x2, z1 - z2);
  }
}
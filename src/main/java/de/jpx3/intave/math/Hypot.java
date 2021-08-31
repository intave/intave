package de.jpx3.intave.math;

// https://stackoverflow.com/questions/3764978/why-hypot-function-is-so-slow
public final class Hypot {
  private static final double TWO_POW_450 = Double.longBitsToDouble(0x5C10000000000000L);
  private static final double TWO_POW_N450 = Double.longBitsToDouble(0x23D0000000000000L);
  private static final double TWO_POW_750 = Double.longBitsToDouble(0x6ED0000000000000L);
  private static final double TWO_POW_N750 = Double.longBitsToDouble(0x1110000000000000L);

  public static double fast(double x, double y) {
    x = Math.abs(x);
    y = Math.abs(y);
    if (y < x) {
      double a = x;
      x = y;
      y = a;
    } else if (!(y >= x)) { // Testing if we have some NaN.
      if (x == Double.POSITIVE_INFINITY) {
        return Double.POSITIVE_INFINITY;
      } else {
        return Double.NaN;
      }
    }
    if (y - x == y) { // x too small to substract from y
      return y;
    } else {
      double factor;
      if (x > TWO_POW_450) { // 2^450 < x < y
        x *= TWO_POW_N750;
        y *= TWO_POW_N750;
        factor = TWO_POW_750;
      } else if (y < TWO_POW_N450) { // x < y < 2^-450
        x *= TWO_POW_750;
        y *= TWO_POW_750;
        factor = TWO_POW_N750;
      } else {
        factor = 1.0;
      }
      return factor * Math.sqrt(x * x + y * y);
    }
  }

  public static double accurate(double x, double y) {
    if (Double.isInfinite(x) || Double.isInfinite(y)) return Double.POSITIVE_INFINITY;
    if (Double.isNaN(x) || Double.isNaN(y)) return Double.NaN;
    x = Math.abs(x);
    y = Math.abs(y);
    if (x < y) {
      double d = x;
      x = y;
      y = d;
    }
    int xi = Math.getExponent(x);
    int yi = Math.getExponent(y);
    if (xi > yi + 27) return x;
    int bias = 0;
    if (xi > 510 || xi < -511) {
      bias = xi;
      x = Math.scalb(x, -bias);
      y = Math.scalb(y, -bias);
    }
    // translated from "Freely Distributable Math Library" e_hypot.c to minimize rounding errors
    double z = 0;
    if (x > 2 * y) {
      double x1 = Double.longBitsToDouble(Double.doubleToLongBits(x) & 0xffffffff00000000L);
      double x2 = x - x1;
      z = Math.sqrt(x1 * x1 + (y * y + x2 * (x + x1)));
    } else {
      double t = 2 * x;
      double t1 = Double.longBitsToDouble(Double.doubleToLongBits(t) & 0xffffffff00000000L);
      double t2 = t - t1;
      double y1 = Double.longBitsToDouble(Double.doubleToLongBits(y) & 0xffffffff00000000L);
      double y2 = y - y1;
      double x_y = x - y;
      z = Math.sqrt(t1 * y1 + (x_y * x_y + (t1 * y2 + t2 * y))); // Note: 2*x*y <= x*x + y*y
    }
    if (bias == 0) {
      return z;
    } else {
      return Math.scalb(z, bias);
    }
  }
}

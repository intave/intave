package de.jpx3.intave.math;

public final class SinusCache {
  private static final float[] SIN_TABLE_FAST = new float[4096];

  /**
   * A table of sin values computed from 0 (inclusive) to 2*pi (exclusive), with steps of 2*PI / 65536.
   */
  private static final float[] SIN_TABLE = new float[65536];

  /**
   * sin looked up in a table
   */
  public static float sin(float value, boolean fastMath) {
    return fastMath
      ? SIN_TABLE_FAST[(int) (value * 651.8986F) & 4095]
      : SIN_TABLE[(int) (value * 10430.378F) & 65535];
  }

  /**
   * cos looked up in the sin table with the appropriate offset
   */
  public static float cos(float value, boolean fastMath) {
    return fastMath
      ? SIN_TABLE_FAST[(int) ((value + (float) Math.PI / 2F) * 651.8986F) & 4095]
      : SIN_TABLE[(int) (value * 10430.378F + 16384.0F) & 65535];
  }

  public static void setup() {
    for (int i = 0; i < 65536; ++i) {
      SIN_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0D / 65536.0D);
    }
    // 1.8.9 optifine H5
    for (int i = 0; i < 4096; ++i) {
      SIN_TABLE_FAST[i] = roundToFloat(Math.sin((double)i * Math.PI * 2d / 4096d));
    }
  }

  public static float roundToFloat(double d) {
    return (float)((double)Math.round(d * 100000000) / 100000000);
  }
}
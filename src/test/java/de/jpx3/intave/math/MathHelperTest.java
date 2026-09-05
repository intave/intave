package de.jpx3.intave.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MathHelperTest {

  @Test
  void formatDoublePadsTheFractionToTheRequestedNumberOfDigits() {
    assertEquals("12", MathHelper.formatDouble(12.0, 0));
    assertEquals("12.00", MathHelper.formatDouble(12.0, 2));
    assertEquals("0.0000", MathHelper.formatDouble(0.0, 4));
    assertEquals("0.006", MathHelper.formatDouble(0.006, 3));
  }

  @Test
  void formatDoubleRoundsHalfUpForPositiveAndNegativeValues() {
    assertEquals("1.13", MathHelper.formatDouble(1.125, 2));
    assertEquals("-1.13", MathHelper.formatDouble(-1.125, 2));
    assertEquals("1.12", MathHelper.formatDouble(1.124, 2));
    assertEquals("-1.12", MathHelper.formatDouble(-1.124, 2));
  }

  @Test
  void formatDoubleCarriesRoundingIntoTheIntegerPart() {
    assertEquals("10.00", MathHelper.formatDouble(9.999, 2));
    assertEquals("-10.00", MathHelper.formatDouble(-9.999, 2));
  }

  @Test
  void formatDoubleSupportsMoreThanTenFractionDigits() {
    assertEquals("1.12500000000", MathHelper.formatDouble(1.125, 11));
    assertEquals("-1.12500000000", MathHelper.formatDouble(-1.125, 11));
  }

  @Test
  void formatDoubleSupportsValuesOutsideTheFastPathRange() {
    assertEquals("1000000.00", MathHelper.formatDouble(1_000_000.0, 2));
    assertEquals("1000000.01", MathHelper.formatDouble(1_000_000.005, 2));
    assertEquals("-1000000.01", MathHelper.formatDouble(-1_000_000.005, 2));
  }

  @Test
  void formatDoubleHandlesNonFiniteValues() {
    assertEquals("NaN", MathHelper.formatDouble(Double.NaN, 2));
    assertEquals("Infinite", MathHelper.formatDouble(Double.POSITIVE_INFINITY, 2));
    assertEquals("Infinite", MathHelper.formatDouble(Double.NEGATIVE_INFINITY, 2));
  }
}

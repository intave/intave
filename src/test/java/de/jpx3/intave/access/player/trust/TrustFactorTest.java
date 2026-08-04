package de.jpx3.intave.access.player.trust;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrustFactorTest {

  /**
   * Both directions used to clamp against {@code values().length}, which indexes one past
   * the end of the array: the step down from the lowest level threw
   * {@link ArrayIndexOutOfBoundsException}. It is reachable from
   * {@code StorageTrustfactorResolver}, which steps down once per past violation.
   */
  @Test
  void steppingPastEitherEndSaturatesInsteadOfThrowing() {
    assertEquals(TrustFactor.DARK_RED, TrustFactor.DARK_RED.unsafer());
    assertEquals(TrustFactor.BYPASS, TrustFactor.BYPASS.unsafer());
    assertEquals(TrustFactor.GREEN, TrustFactor.GREEN.safer());
  }

  @Test
  void stepsMoveExactlyOneLevel() {
    assertEquals(TrustFactor.YELLOW, TrustFactor.GREEN.unsafer());
    assertEquals(TrustFactor.ORANGE, TrustFactor.YELLOW.unsafer());
    assertEquals(TrustFactor.RED, TrustFactor.ORANGE.unsafer());
    assertEquals(TrustFactor.DARK_RED, TrustFactor.RED.unsafer());

    assertEquals(TrustFactor.RED, TrustFactor.DARK_RED.safer());
    assertEquals(TrustFactor.YELLOW, TrustFactor.ORANGE.safer());
  }

  /**
   * The trustfactor.yml rows are positional -- one value per enum constant, in declaration
   * order -- so the order is part of the file format, not an implementation detail.
   */
  @Test
  void declarationOrderRunsFromMostToLeastTrusted() {
    TrustFactor[] values = TrustFactor.values();
    for (int i = 1; i < values.length; i++) {
      assertEquals(true, values[i - 1].factor() > values[i].factor(),
        values[i - 1] + " should rank above " + values[i]);
    }
  }
}

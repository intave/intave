package de.jpx3.intave.check.other.automationanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class PitchLockedPathingPatternTests {
  @Test
  public void reportsPitchLockedPathingAfterLongPitchStableMovement() {
    PitchLockedPathingPattern pattern = new PitchLockedPathingPattern();
    PitchLockedPathingPattern.MovementResult result = PitchLockedPathingPattern.MovementResult.empty();
    boolean suspicious = false;

    for (int i = 0; i <= 80; i++) {
      result = pattern.pushMovement(i * 0.27d, 64.0d, 0.0d, i * 0.9f, 12.0f);
      suspicious |= result.suspicious();
    }

    assertTrue(suspicious);
  }

  @Test
  public void ignoresPitchLockedPathingWhenPitchChanges() {
    PitchLockedPathingPattern pattern = new PitchLockedPathingPattern();
    PitchLockedPathingPattern.MovementResult result = PitchLockedPathingPattern.MovementResult.empty();

    for (int i = 0; i <= 80; i++) {
      float pitch = 12.0f + (i % 4 == 0 ? 0.25f : 0.0f);
      result = pattern.pushMovement(i * 0.27d, 64.0d, 0.0d, i * 0.9f, pitch);
    }

    assertFalse(result.suspicious());
  }
}

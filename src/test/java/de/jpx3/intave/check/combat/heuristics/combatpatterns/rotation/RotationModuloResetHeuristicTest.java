package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationModuloResetHeuristic.isSuspiciousYawJump;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationModuloResetHeuristicTest {

  // --- isSuspiciousYawJump: yaw-distance math, positive/negative combinations ---

  @Test
  void flagsLargeJumpBetweenTwoPositiveYaws() {
    assertTrue(isSuspiciousYawJump(150f, 10f));
  }

  @Test
  void flagsLargeJumpBetweenTwoNegativeYaws() {
    assertTrue(isSuspiciousYawJump(-150f, -10f));
  }

  @Test
  void flagsLargeJumpFromNegativeToPositiveYaw() {
    assertTrue(isSuspiciousYawJump(60f, -60f));
  }

  @Test
  void flagsLargeJumpFromPositiveToNegativeYaw() {
    assertTrue(isSuspiciousYawJump(-60f, 60f));
  }

  @Test
  void doesNotFlagSmallRotationChange() {
    assertFalse(isSuspiciousYawJump(15f, 10f));
  }

  @Test
  void doesNotFlagJumpExactlyAtThreshold() {
    assertFalse(isSuspiciousYawJump(100f, 0f));
  }

  @Test
  void doesNotFlagWhenRotationYawExceedsRoundingBound() {
    assertFalse(isSuspiciousYawJump(400f, 0f));
  }

  @Test
  void doesNotFlagWhenLastRotationYawExceedsRoundingBound() {
    assertFalse(isSuspiciousYawJump(0f, 400f));
  }
}

package de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation;

import org.junit.jupiter.api.Test;

import static de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationSnapHeuristic.computeYawMotion;
import static de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationSnapHeuristic.isRotationSnapDetected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationSnapHeuristicTest {
  // rotationPacketCounter and the swing/attack-recency gate are not under test here;
  // hold them at values that always satisfy the gate so only yaw motion + teleport ticks vary.
  private static final int DEFAULT_ROTATION_PACKET_COUNTER = 20;
  private static final boolean DEFAULT_RECENT_SWING_OR_ATTACK = true;
  private static final int PAST_TELEPORT_TICKS = 8;

  private boolean detected(double previousYawMotion, double lastYawMotion, double currentYawMotion, int ticksPastTeleport) {
    return isRotationSnapDetected(
      previousYawMotion, lastYawMotion, currentYawMotion,
      DEFAULT_RECENT_SWING_OR_ATTACK, DEFAULT_ROTATION_PACKET_COUNTER, ticksPastTeleport
    );
  }

  @Test
  void yawMotionOfPositiveToPositiveRotation() {
    assertEquals(5.0, computeYawMotion(10f, 15f));
  }

  @Test
  void yawMotionOfNegativeToNegativeRotation() {
    assertEquals(5.0, computeYawMotion(-10f, -15f));
  }

  @Test
  void yawMotionOfPositiveToNegativeRotationCrossingZero() {
    assertEquals(10.0, computeYawMotion(5f, -5f));
  }

  @Test
  void yawMotionOfNegativeToPositiveRotationCrossingZero() {
    assertEquals(10.0, computeYawMotion(-5f, 5f));
  }

  @Test
  void yawMotionIsSymmetric() {
    assertEquals(computeYawMotion(30f, -20f), computeYawMotion(-20f, 30f));
  }

  @Test
  void yawMotionOfNoRotationIsZero() {
    assertEquals(0.0, computeYawMotion(-45f, -45f));
  }

  @Test
  void flagsWhenQuietThenSnapThenSettledWithPositiveYaws() {
    // two ticks ago: quiet (<9), last tick: snap (>40), current tick: settled (<9)
    assertTrue(detected(0, 45, 0, PAST_TELEPORT_TICKS));
  }

  @Test
  void flagsWhenQuietThenSnapThenSettledWithNegativeYaws() {
    // yaw motion is always a magnitude (see computeYawMotion), but the snap can be
    // produced by a rotation from a negative to a positive angle or vice versa.
    double snapMotion = computeYawMotion(-30f, 20f);
    assertTrue(detected(0, snapMotion, 0, PAST_TELEPORT_TICKS));
  }

  @Test
  void doesNotFlagWhenNoPriorQuietTick() {
    // previousYawMotion must be < 9; a player who was already moving their view fast doesn't count.
    assertFalse(detected(15, 45, 0, PAST_TELEPORT_TICKS));
  }

  @Test
  void doesNotFlagWhenSnapIsBelowThreshold() {
    // lastYawMotion must exceed 40 to be considered a snap.
    assertFalse(detected(0, 40, 0, PAST_TELEPORT_TICKS));
  }

  @Test
  void doesNotFlagWhenViewDoesNotSettleAfterSnap() {
    // currentYawMotion must drop back below 9 after the snap.
    assertFalse(detected(0, 45, 9, PAST_TELEPORT_TICKS));
  }

  @Test
  void doesNotFlagExactlyAtTeleportBoundary() {
    assertFalse(detected(0, 45, 0, 7));
  }

  @Test
  void flagsOnceEnoughTicksHavePassedSinceTeleport() {
    assertTrue(detected(0, 45, 0, 8));
  }
}

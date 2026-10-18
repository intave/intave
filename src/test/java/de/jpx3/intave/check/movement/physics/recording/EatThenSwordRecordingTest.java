/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.movement.physics.recording;

import org.junit.jupiter.api.Test;

import java.io.IOException;

final class EatThenSwordRecordingTest {
  @Test
  void replaysMinecraft1205ClientOnMinecraft18Server() throws IOException {
    MovementRecordingPhysicsTests.processRecordingResource(
      "physics_test_runs/use/eat_then_sword_1.20.5_on_1.8.ptr"
    );
  }

  @Test
  void replaysMinecraft263ClientOnMinecraft18Server() throws IOException {
    MovementRecordingPhysicsTests.processRecordingResource(
      "physics_test_runs/use/eat_then_sword_26.3_on_1.8.ptr"
    );
  }
}

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

package de.jpx3.intave.report;

import com.google.gson.JsonObject;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PhysicsReportTest {
  @Test
  void reportsEachPhysicsInputOnceWithoutUnrelatedMetadata() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.21.4"));
    User user = UserFactory.createFallback();
    ViolationMetadata violations = user.meta().violationLevel();
    violations.physicsOffset = 0.75;
    violations.physicsVL = 42;
    violations.physicsInsignificantBufferVL = 1.25;
    violations.physicsVelocityVL = 2.5;
    violations.physicsInvalidMovementsInRow = 3;
    violations.backtrackVL = 99;
    violations.wrappedNoSlowdownVL = 88;
    violations.detectionCounter = 77;

    JsonObject report = new PhysicsReport(user).toJson();

//    assertEquals(10, report.entrySet().size());
    assertFalse(report.has("evaluator"));
    assertFalse(report.has("violations"));

    JsonObject movement = report.getAsJsonObject("movement");
    assertTrue(movement.has("positions"));
    assertTrue(movement.has("motions"));
    assertTrue(movement.has("state"));
    assertTrue(movement.has("surroundingBlocks"));
    assertTrue(movement.has("tolerances"));
    assertTrue(movement.has("packetHistory"));
    assertFalse(movement.has("positionX"));
    assertFalse(movement.has("baseMotionX"));
    assertFalse(movement.has("serverMovementDebugValues"));

    JsonObject surroundingBlocks = movement.getAsJsonObject("surroundingBlocks");
    assertTrue(surroundingBlocks.has("minInclusive"));
    assertTrue(surroundingBlocks.has("maxInclusive"));
    assertTrue(surroundingBlocks.get("complete").getAsBoolean());
    assertTrue(surroundingBlocks.get("nonAirBlocks").getAsJsonArray().isEmpty());

    JsonObject input = movement.getAsJsonObject("input");
    assertFalse(input.has("forward"));
    assertFalse(input.has("strafe"));

    JsonObject motions = movement.getAsJsonObject("motions");
    assertTrue(motions.has("sentOffset"));
    assertTrue(motions.get("simulatedOffset").isJsonNull());
    assertTrue(motions.get("actual").isJsonNull());
    assertFalse(motions.has("receivedOffset"));
    assertFalse(motions.has("resetX"));
    assertFalse(motions.has("resetZ"));

    JsonObject state = movement.getAsJsonObject("state");
    assertFalse(state.has("onGround"));
    assertFalse(state.has("collidedHorizontally"));
    assertFalse(state.has("collidedVertically"));
    assertFalse(state.has("jumping"));

    JsonObject configuration = movement.getAsJsonObject("lastMovementConfiguration");
    assertFalse(configuration.has("keys"));
    assertFalse(configuration.has("reducing"));

    JsonObject evaluationState = movement.getAsJsonObject("evaluationState");
    assertEquals(5, evaluationState.entrySet().size());
    assertEquals(0.75, evaluationState.get("physicsOffset").getAsDouble());
    assertEquals(42, evaluationState.get("physicsViolationLevel").getAsDouble());
    assertEquals(3, evaluationState.get("invalidMovementsInRow").getAsDouble());

    assertFalse(report.getAsJsonObject("protocol").has("clientBrand"));
    assertFalse(report.getAsJsonObject("protocol").has("locale"));
    assertFalse(report.getAsJsonObject("abilities").has("health"));
    assertFalse(report.getAsJsonObject("inventory").has("windowClickCounter"));

    String json = report.toString();
    assertFalse(json.contains("backtrackVL"));
    assertFalse(json.contains("wrappedNoSlowdownVL"));
    assertFalse(json.contains("detectionCounter"));
    assertFalse(json.contains("\"violationLevel\""));
  }

  @Test
  void surroundingBlocksCoverReceivedAndSimulatedMovement() {
    MinecraftVersion.setCurrent(new MinecraftVersion("1.21.4"));
    User user = UserFactory.createFallback();
    MovementMetadata movement = user.meta().movement();
    movement.setVerifiedLastPosition(new Position(0, 0, 0), "test");
    movement.setPosition(3, 0, 0);
    movement.setSimulationResult(new SimulationResult(
      new Motion(-3, 0, 0),
      new Motion(-0.1, 0, 0),
      new Motion(),
      false,
      false,
      false,
      false,
      false,
      false,
      false,
      0
    ));

    JsonObject movementJson = new PhysicsReport(user).toJson().getAsJsonObject("movement");
    JsonObject surroundingBlocks = movementJson.getAsJsonObject("surroundingBlocks");
    int minX = surroundingBlocks.getAsJsonObject("minInclusive").get("x").getAsInt();
    int maxX = surroundingBlocks.getAsJsonObject("maxInclusive").get("x").getAsInt();

    assertTrue(minX <= -5);
    assertTrue(maxX >= 4);
    JsonObject motions = movementJson.getAsJsonObject("motions");
    assertEquals(-0.1, motions.getAsJsonObject("simulatedOffset").get("x").getAsDouble());
    assertEquals(-3, motions.getAsJsonObject("actual").get("x").getAsDouble());
  }
}

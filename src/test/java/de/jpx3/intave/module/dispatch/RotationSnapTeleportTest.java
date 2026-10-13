package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationSnapHeuristic.computeYawMotion;
import static de.jpx3.intave.check.combat.heuristics.combatpatterns.rotation.RotationSnapHeuristic.isRotationSnapDetected;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Feeds the yaw motion and teleport ticks produced by teleport handling into the rotation snap detection
class RotationSnapTeleportTest {
  // The swing/attack-recency and startup gates are not under test here; hold them at values that pass.
  private static final boolean RECENT_SWING_OR_ATTACK = true;
  private static final int ROTATION_PACKET_COUNTER = 20;
  private static final Position TARGET = new Position(0, 70, 0);

  private final TeleportController controller = new TeleportController((u, task) -> task.run(), (u, tp) -> tp.allow());
  private final double[] yawMotions = new double[2];
  private User user;
  private MovementMetadata movement;

  @BeforeEach
  void setup() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(774);
    movement = user.meta().movement();
    movement.setVerifiedLastPosition(new Position(0, 64, 0), "test baseline");
    movement.setBaseMotion(Motion.newEmpty());
    movement.setPast(TELEPORT, 100);
  }

  @Test
  void flagsSnapMadeByThePlayer() {
    // Sanity check: the same yaw pattern as below flags without a teleport
    boolean flagged = look(0, 0, 0);
    flagged |= look(270, 270, 270);
    assertTrue(flagged);
  }

  @Test
  void doesNotFlagTurnCausedByAbsoluteTeleport() {
    boolean flagged = look(0, 0, 0);
    controller.teleport(user, PositionMoveRotation.withoutMotion(TARGET, new Rotation(270, 0)), EnumSet.noneOf(Relative.class));
    flagged |= confirmationMove(270);
    flagged |= look(270, 270, 270, 270, 270, 270, 270, 270, 270, 270);
    assertFalse(flagged);
  }

  @Test
  void doesNotFlagTurnsMadeWhileASetbackIsPending() {
    boolean flagged = look(0, 0, 0);
    controller.movementCorrection(user, PositionMoveRotation.withoutRotation(TARGET, Motion.newEmpty()));
    // Movement is dropped while the setback is pending, but the client keeps turning
    flagged |= droppedMove(3);
    flagged |= confirmationMove(60);
    flagged |= look(62, 64, 66, 68, 70, 72, 74, 76, 78, 80);
    assertFalse(flagged);
  }

  // Rotation-only movement packets, as applied by MovementMetadata#updateMovement
  private boolean look(float... yaws) {
    boolean flagged = false;
    for (float yaw : yaws) {
      movement.setLastRotation(movement.rotation());
      movement.setRotation(yaw, 0);
      flagged |= completeMovement();
    }
    return flagged;
  }

  private boolean confirmationMove(float yaw) {
    movement.sentTeleportIdBefore = true;
    movement.lastTeleportAcceptId = movement.pendingTeleports.get().peekFirst().id().orElse(0);
    assertTrue(controller.confirmTeleport(user, TARGET, new Rotation(yaw, 0)));
    movement.isTeleportConfirmationPacket = true;
    return completeMovement();
  }

  private boolean droppedMove(int packets) {
    boolean flagged = false;
    for (int i = 0; i < packets; i++) {
      flagged |= completeMovement();
    }
    return flagged;
  }

  // Mirrors the detection in RotationSnapHeuristic#receiveMovementPacket, followed by the tick completion
  private boolean completeMovement() {
    boolean flagged = false;
    if (movement.ticksPast(TELEPORT) != 0) {
      double yawMotion = computeYawMotion(movement.lastRotationYaw, movement.rotationYaw);
      flagged = isRotationSnapDetected(
        yawMotions[1], yawMotions[0], yawMotion,
        RECENT_SWING_OR_ATTACK, ROTATION_PACKET_COUNTER, movement.ticksPast(TELEPORT)
      );
      yawMotions[1] = yawMotions[0];
      yawMotions[0] = yawMotion;
    }
    movement.tickComplete(true, true, true);
    return flagged;
  }
}

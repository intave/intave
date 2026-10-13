package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.branch.*;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.update.*;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SetbackVelocityOrderingTest {
  private static final Position TARGET = new Position(0, 64, 0);
  private static final Motion CORRECTION = new Motion(0.125, -0.25, 0.375);
  private static final Motion VELOCITY = new Motion(0.5, 0.25, -0.5);
  private static final Motion EXPLOSION = new Motion(0.125, 0.125, 0.125);

  @Test void preservesVelocityBeforeCreationAndBetweenCreationAndTransmission() {
    for (boolean legacy : new boolean[] {false, true}) {
      for (boolean beforeRequest : new boolean[] {false, true}) {
        for (boolean feedbackReceived : new boolean[] {false, true}) {
          Harness h = new Harness(legacy);
          if (!beforeRequest) h.request();
          MotionUpdate velocity = h.velocity();
          if (feedbackReceived) velocity.activate(h.movement);
          if (beforeRequest) h.request();
          h.send();
          assertEquals(VELOCITY, h.sent.get(0).change().motion());
          velocity.activate(h.movement); // A late receipt must not replay the covered update.
          velocity.canNotRunAfterThisTick(h.movement);
          h.confirm();
          assertEquals(VELOCITY, h.movement.mutableBaseMotionCopy());
          assertFalse(velocity.possible(h.movement));
        }
      }
    }
  }

  @Test void appliesReplacementAndAdditivePacketsInSendOrderOnlyOnce() {
    for (boolean legacy : new boolean[] {false, true}) {
      Harness h = new Harness(legacy);
      h.request();
      h.explosion(); // Superseded by the later replacement velocity.
      h.velocity();
      h.explosion();
      h.send();
      Motion expected = VELOCITY.copy().add(EXPLOSION);
      for (int retry = 0; retry < 3; retry++) {
        assertEquals(expected, h.sent.get(h.sent.size() - 1).change().motion());
        h.controller.onResendTimeout(h.user);
      }
      h.confirm();
      assertEquals(expected, h.movement.mutableBaseMotionCopy());
    }
  }

  @Test void doesNotReplayVelocityAlreadyIncorporatedByTheSimulation() {
    Harness h = new Harness(false);
    MotionUpdate velocity = h.velocity();
    velocity.activate(h.movement);
    h.movement.setActiveSequence(velocity.constraint().sequenceNumber());
    h.request();
    h.send();
    h.confirm();
    assertEquals(CORRECTION, h.movement.mutableBaseMotionCopy());
  }

  @Test void retryPreservesNewerVelocityInsteadOfRestoringOldCorrectionMotion() {
    for (boolean legacy : new boolean[] {false, true}) {
      Harness h = new Harness(legacy);
      h.request();
      h.send();
      MotionUpdate velocity = h.velocity();
      velocity.activate(h.movement);
      velocity.canNotRunAfterThisTick(h.movement);
      h.controller.onResendTimeout(h.user);
      h.controller.onResendTimeout(h.user);
      h.confirm();
      assertEquals(VELOCITY, h.movement.mutableBaseMotionCopy());
      assertFalse(velocity.possible(h.movement));
    }
  }

  @Test void velocitySentAfterCorrectionSurvivesBlockedTicksAndConfirmation() {
    for (boolean additive : new boolean[] {false, true}) {
      Harness h = new Harness(false);
      h.request();
      h.send();
      MotionUpdate velocity = additive ? h.explosion() : h.velocity();
      velocity.activate(h.movement);
      velocity.canNotRunAfterThisTick(h.movement);
      for (int tick = 0; tick < 3; tick++) h.movement.tickComplete(true, false, true);
      h.confirm();
      h.movement.tickComplete(true, false, true);
      assertEquals(CORRECTION, h.movement.mutableBaseMotionCopy());
      MovementSearchInput input = MovementSearchInput.forTick(h.user,
        h.movement.simulator(), h.movement, false);
      List<MovementSearchBranch> branches = new ArrayList<>();
      new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);
      assertEquals(1, branches.size(), "Acknowledged velocity must not be optional after recovery");
      SimulationEnvironment next = branches.get(0).modifiedMutableView(h.movement);
      assertEquals(additive ? CORRECTION.copy().add(EXPLOSION) : VELOCITY,
        next.mutableBaseMotionCopy());
      assertEquals(velocity.constraint().sequenceNumber(), next.activeSequence());
      h.movement.setActiveSequence(next.activeSequence());
      assertTrue(velocity.expired(h.movement));
    }
  }

  @Test void transactionTriggeredGroundOnlyTickCannotDiscardVelocity() {
    for (boolean additive : new boolean[] {false, true}) {
      Harness h = new Harness(true);
      MotionUpdate velocity = additive ? h.explosion() : h.velocity();
      velocity.activate(h.movement);
      velocity.canNotRunAfterThisTick(h.movement);

      h.movement.tickComplete(false, false, true);

      assertTrue(h.movement.currentTick() > velocity.constraint().notAfter());
      assertTrue(h.movement.allTickAmbiguousUpdates().contains(velocity));
      assertTrue(velocity.possible(h.movement));

      MovementSearchInput input = MovementSearchInput.forTick(h.user,
        h.movement.simulator(), h.movement, false);
      List<MovementSearchBranch> branches = new ArrayList<>();
      new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);
      assertEquals(1, branches.size(), "The next explicit movement must apply the retained velocity");
      SimulationEnvironment next = branches.get(0).modifiedMutableView(h.movement);
      assertEquals(additive ? EXPLOSION : VELOCITY, next.mutableBaseMotionCopy());
      assertEquals(velocity.constraint().sequenceNumber(), next.activeSequence());
    }
  }

  @Test void externalTeleportStillSupersedesScheduledCorrectionAndOldVelocity() {
    Harness h = new Harness(false);
    h.request();
    MotionUpdate velocity = h.velocity();
    velocity.activate(h.movement);
    h.controller.teleport(h.user, PositionMoveRotation.withoutRotation(TARGET, Motion.newEmpty()),
      Relative.RELATIVE_ROTATION);
    h.send();
    h.confirm();
    velocity.canNotRunAfterThisTick(h.movement);
    h.movement.tickComplete(true, false, true);
    assertFalse(velocity.possible(h.movement));
    h.request();
    h.send();
    assertEquals(CORRECTION, h.sent.get(1).change().motion());
  }

  @Test void externalTeleportRetryPreservesOnlyVelocitySentAfterTheTeleport() {
    Harness h = new Harness(false);
    MotionUpdate older = h.velocity();
    h.controller.teleport(h.user, PositionMoveRotation.withoutRotation(TARGET, CORRECTION),
      Relative.RELATIVE_ROTATION);
    h.explosion();
    h.controller.onResendTimeout(h.user);
    h.confirm();
    assertEquals(CORRECTION.copy().add(EXPLOSION), h.movement.mutableBaseMotionCopy());
    older.activate(h.movement);
    assertFalse(older.possible(h.movement));
  }

  private static class Harness {
    final User user = UserFactory.createFallback();
    final MovementMetadata movement = user.meta().movement();
    final Queue<Runnable> scheduled = new ArrayDeque<>();
    final List<Teleport> sent = new ArrayList<>();
    final TeleportController controller = new TeleportController(
      (u, task) -> scheduled.add(task), (u, teleport) -> sent.add(teleport));

    Harness(boolean legacy) {
      MinecraftVersion.setCurrent(legacy ? MinecraftVersions.VER1_8_0 : MinecraftVersions.VER1_21_4);
      user.meta().protocol().setProtocolVersion(legacy ? 47 : 768);
      movement.setVerifiedLastPosition(TARGET, "velocity regression baseline");
    }

    MotionUpdate velocity() {
      MotionUpdate update = MotionUpdate.pendingReplacement(VELOCITY);
      movement.trackVelocity(update);
      return update;
    }

    MotionUpdate explosion() {
      MotionUpdate update = MotionUpdate.pendingAddition(EXPLOSION);
      movement.trackVelocity(update);
      return update;
    }

    void request() {
      controller.movementCorrection(user, PositionMoveRotation.withoutRotation(TARGET, CORRECTION));
    }

    void send() { scheduled.remove().run(); }

    void confirm() {
      Teleport teleport = movement.pendingTeleports.get().peekFirst();
      controller.beforeTeleportTransactionReceive(user, teleport);
      movement.lastTeleportAcceptId = teleport.id().orElse(0);
      movement.sentTeleportIdBefore = true;
      assertTrue(controller.confirmTeleport(user, TARGET, Rotation.zero()));
    }
  }
}

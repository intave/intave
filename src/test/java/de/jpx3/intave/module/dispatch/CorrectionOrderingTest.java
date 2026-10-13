package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.block.cache.MockFullBlockStaticPlane;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.test.FakeWorldFactory;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.UUID;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_11;
import static org.junit.jupiter.api.Assertions.*;

class CorrectionOrderingTest {
  private static final Position START = new Position(-18.66825686763305, 84.49520087700593, -81.77324752119922);
  private static final Position TARGET = new Position(-18.575078110727343, 84.1212968405392, -81.54535828436155);
  private static final Position ILLEGAL = new Position(-18.365979392923922, 84.59520087849604, -78.7800301178712);
  private static final Motion MOTION = new Motion(0.0847926712279048, -0.4448259643949201, 0.2073792114989082);

  @BeforeAll static void version() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test void packetlogDoubleMoveCannotProduceASecondContaminatedCorrection() {
    Harness h = new Harness();
    Position mutable = Position.mutableCopy(TARGET);
    Motion motion = MOTION.copy();
    h.controller.movementCorrection(h.user, PositionMoveRotation.withoutRotation(mutable, motion));
    mutable.setX(500);
    motion.setMotionZ(500);
    assertFalse(h.controller.processMovementPackets(h.user));
    h.move();
    h.controller.movementCorrection(h.user, PositionMoveRotation.withoutRotation(ILLEGAL, new Motion(0, 0, 2.5011235604565423)));
    assertEquals(1, h.scheduled.size());
    h.send();
    assertEquals(TARGET, h.sent.get(0).change().position());
    assertEquals(MOTION, h.sent.get(0).change().motion());
    h.finish();
    assertEquals(TARGET, h.movement.verifiedLastPosition());
    assertEquals(MOTION, h.movement.mutableBaseMotionCopy());
    assertEquals(TARGET, h.serverPosition);
  }

  @Test void simulatedGroundStateIsAppliedOnlyOnConfirmationAndSurvivesRetry() {
    for (boolean grounded : new boolean[] {false, true}) {
      Harness h = new Harness();
      h.movement.onGround = !grounded;
      h.movement.setLastOnGround(!grounded);
      h.controller.movementCorrection(h.user, new MovementCorrection(
        PositionMoveRotation.withoutRotation(TARGET, MOTION), grounded));
      h.send();
      h.controller.onResendTimeout(h.user);
      assertEquals(!grounded, h.movement.lastOnGround());
      assertEquals(grounded, h.sent.get(1).simulatedOnGround());
      h.finish();
      assertEquals(grounded, h.movement.lastOnGround());
      assertEquals(grounded, h.movement.onGround());
    }
  }

  @Test void repeatedAirborneCorrectionsWithJumpHeldCannotClimb() {
    World world = FakeWorldFactory.createWorld((name, args) -> switch (name) {
      case "isChunkLoaded", "isChunkInUse" -> true;
      case "isThundering", "hasStorm" -> false;
      default -> null;
    });
    Player player = FakePlayerFactory.createPlayer((name, args) -> switch (name) {
      case "getWorld" -> world;
      case "getLocation" -> START.toLocation(world);
      case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-000000000002");
      default -> null;
    });
    User user = UserFactory.createTestUserFor(player, (usr, key) -> switch (key) {
      case "blockCache" -> new MockFullBlockStaticPlane();
      case "protocolVersion" -> VER_1_21_11;
      default -> null;
    });
    UserRepository.manuallyRegisterUser(player, user);
    Harness h = new Harness(user);
    h.movement.onGround = true;
    h.movement.setLastOnGround(true);
    h.movement.setJumpMotion((double) 0.42F);
    h.movement.gravity = 0.08;
    MovementConfiguration jumping = MovementConfiguration.blank().withJump();
    double expectedY = START.getY();
    double expectedVelocity = (double) 0.42F;
    for (int tick = 0; tick < 30; tick++) {
      Simulation simulation = h.movement.simulator().simulateTick(user,
        h.movement.mutableBaseMotionCopy(), h.movement.immutableView(), jumping).reusableCopy();
      MovementCorrection correction = simulation.setbackPosition(user, 0);
      expectedY += expectedVelocity;
      expectedVelocity = (expectedVelocity - 0.08) * (double) 0.98F;
      h.controller.movementCorrection(user, correction);
      h.send();
      if (tick % 3 == 0) h.controller.onResendTimeout(user);
      h.finish();
      assertFalse(h.movement.lastOnGround(), "tick " + tick);
      assertEquals(expectedY, h.movement.verifiedLastPositionY(), 1.0E-9, "tick " + tick);
      assertEquals(expectedVelocity, h.movement.baseMotionY(), 1.0E-9, "tick " + tick);
    }
    assertTrue(h.movement.verifiedLastPositionY() < START.getY());
  }

  @Test void invalidMovementCannotReachServerOrCommitEvenWithoutRecovery() {
    Harness h = new Harness();
    h.movement.invalidMovement = true;
    h.movement.setPosition(ILLEGAL);
    assertTrue(MovementDispatcher.mustRejectMovement(h.movement));
    assertFalse(MovementDispatcher.canCommitMovement(h.movement, false));
    h.movement.movementWithheldForTeleport = true;
    assertFalse(MovementDispatcher.canCommitMovement(h.movement, true));
  }

  @Test void pendingTeleportIngestsUntilInvalidMovementThenBlocksUntilConfirmation() {
    for (int protocol : new int[] {47, VER_1_21_11}) {
      Harness h = new Harness();
      h.user.meta().protocol().setProtocolVersion(protocol);
      PositionMoveRotation delta = new PositionMoveRotation(new Position(0, 10, 0),
        new Motion(0, 0.25, 0), new Rotation(15, 5));
      h.controller.teleport(h.user, delta, Relative.ALL_RELATIVE);
      Teleport pending = h.sent.get(0);
      long sequence = h.movement.teleportSequence;

      // Valid pre-teleport ticks still supply the relative teleport's base.
      h.movement.movementWithheldForTeleport = true;
      assertTrue(h.controller.processMovementPackets(h.user));
      assertTrue(MovementDispatcher.canCommitMovement(h.movement, true));
      h.movement.setVerifiedLastPosition(TARGET, "valid in-flight tick");
      h.movement.setBaseMotion(MOTION.copy());
      h.movement.setRotation(new Rotation(30, 10));

      h.controller.movementCorrection(h.user, PositionMoveRotation.withoutRotation(ILLEGAL, MOTION));
      h.controller.movementCorrection(h.user, new MovementCorrection(
        PositionMoveRotation.withoutRotation(ILLEGAL, MOTION), false));
      h.controller.teleport(h.user, PositionMoveRotation.withoutRotation(ILLEGAL, MOTION), Relative.RELATIVE_ROTATION);
      assertTrue(h.movement.invalidMovement);
      assertFalse(MovementDispatcher.canCommitMovement(h.movement, true));
      assertTrue(h.movement.inRecovery);
      assertFalse(h.controller.processMovementPackets(h.user));
      assertFalse(h.controller.forwardMovementPackets(h.user));
      assertTrue(h.scheduled.isEmpty());
      assertEquals(1, h.sent.size());
      assertEquals(sequence, h.movement.teleportSequence);
      assertSame(pending, h.movement.pendingTeleports.get().peekFirst());

      // Per-packet flags resetting must not reopen movement during recovery.
      h.movement.invalidMovement = false;
      h.movement.dropPostTickMotionProcessing = false;
      h.movement.movementWithheldForTeleport = true;
      assertFalse(h.controller.processMovementPackets(h.user));
      assertFalse(MovementDispatcher.canCommitMovement(h.movement, true));
      PositionMoveRotation expected = pending.expectedPositionMoveRotation(
        TARGET, h.movement.lastRotation(), MOTION);
      h.leadingFeedback();
      h.ack();
      assertTrue(h.controller.confirmTeleport(h.user, expected.position(), expected.rotation()));
      assertEquals(expected.position(), h.movement.verifiedLastPosition());
      assertEquals(expected.motion(), h.movement.mutableBaseMotionCopy());
      assertFalse(h.movement.inRecovery);
      assertTrue(h.controller.forwardMovementPackets(h.user));

      h.request();
      assertTrue(h.movement.inRecovery);
      assertEquals(1, h.scheduled.size());
    }
  }

  @Test void ordinaryTeleportCanSupersedeScheduledCorrection() {
    Harness h = new Harness();
    h.request();
    Position destination = new Position(20, 90, 20);
    h.controller.teleport(h.user, PositionMoveRotation.withoutRotation(destination, Motion.newEmpty()), Relative.RELATIVE_ROTATION);
    h.send(); // Stale scheduled correction must not send after the external request.
    assertEquals(1, h.sent.size());
    h.finish();
    assertEquals(destination, h.movement.verifiedLastPosition());
    assertFalse(h.movement.inRecovery);
  }

  @Test void packetlogServerRollbackCannotRetainTheRejectedFlyPosition() {
    Harness h = new Harness();
    Position before = new Position(-16.212164830133574, 84, -48.79326983938645);
    Position rejected = new Position(-14.195353941893169, 84, -46.55078686827883);
    Position corrected = new Position(-16.018879725822234, 84, -48.651372423704494);
    h.serverPosition = before;
    h.movement.setVerifiedLastPosition(before, "second packetlog slip");
    h.movement.setPosition(rejected);
    h.controller.movementCorrection(h.user, PositionMoveRotation.withoutRotation(corrected, Motion.newEmpty()));
    // This is the packet which previously advanced Paper's rollback position.
    if (!MovementDispatcher.mustRejectMovement(h.movement)) h.serverPosition = rejected;
    assertEquals(before, h.serverPosition);
    assertFalse(MovementDispatcher.canCommitMovement(h.movement, false));
    h.send();
    h.leadingFeedback();
    h.ack();
    h.confirm();
    assertEquals(corrected, h.serverPosition);
    assertEquals(corrected, h.movement.verifiedLastPosition());
  }

  @Test void exhaustivelyChecksFiveEventSequences() {
    // 10^5 histories, including stale feedback callbacks and wrong accept IDs.
    // Every run must remain safe and recover once valid feedback is delivered.
    int histories = 100000;
    for (int history = 0; history < histories; history++) {
      Harness h = new Harness();
      h.request();
      int events = history;
      for (int step = 0; step < 5; step++, events /= 10) {
        switch (events % 10) {
          case 0: h.move(); break;
          case 1: h.request(); break;
          case 2: h.send(); break;
          case 3: h.ack(); break;
          case 4: h.confirm(); break;
          case 5: h.controller.onResendTimeout(h.user); break;
          case 6: assertFalse(h.controller.confirmTeleport(h.user, ILLEGAL, Rotation.zero())); break;
          case 7:
            if (!h.sent.isEmpty()) h.controller.beforeTeleportTransactionReceive(h.user, h.sent.get(0));
            break;
          case 8:
            if (!h.sent.isEmpty()) h.controller.afterTeleportTransactionReceive(h.user, h.sent.get(0));
            break;
          case 9:
            h.movement.sentTeleportIdBefore = true;
            h.movement.lastTeleportAcceptId = Integer.MAX_VALUE;
            break;
        }
        Position verified = h.movement.verifiedLastPosition();
        assertTrue(verified.equals(START) || verified.equals(TARGET), "history " + history);
        assertTrue(h.serverPosition.equals(START) || h.serverPosition.equals(TARGET), "history " + history);
      }
      h.finish();
      assertEquals(TARGET, h.movement.verifiedLastPosition(), "history " + history);
      assertEquals(MOTION, h.movement.mutableBaseMotionCopy(), "history " + history);
      assertFalse(h.movement.inRecovery, "history " + history);
    }
  }

  private static final class Harness {
    final User user;
    final MovementMetadata movement;
    final Queue<Runnable> scheduled = new ArrayDeque<>();
    final List<Teleport> sent = new ArrayList<>();
    final TeleportController controller = new TeleportController((u, task) -> scheduled.add(task), (u, packet) -> sent.add(packet));
    Position serverPosition = START;
    Harness() { this(UserFactory.createFallback()); }
    Harness(User user) {
      this.user = user;
      this.movement = user.meta().movement();
      user.meta().protocol().setProtocolVersion(VER_1_21_11);
      movement.setPosition(START);
      movement.setLastPosition(START);
      movement.setVerifiedLastPosition(START, "packetlog baseline");
      movement.setBaseMotion(Motion.newEmpty());
    }
    void request() {
      controller.movementCorrection(user, PositionMoveRotation.withoutRotation(TARGET, MOTION));
    }
    void move() {
      movement.invalidMovement = false;
      movement.dropPostTickMotionProcessing = false;
      if (!controller.processMovementPackets(user)) return;
      movement.setPosition(ILLEGAL);
      movement.invalidMovement = true;
      boolean cancelled = MovementDispatcher.mustRejectMovement(movement);
      if (!cancelled && controller.forwardMovementPackets(user)) serverPosition = ILLEGAL;
      if (MovementDispatcher.canCommitMovement(movement, cancelled)) {
        movement.setVerifiedLastPosition(ILLEGAL, "bad commit");
        movement.setBaseMotion(new Motion(0, 0, 2.7238279155287466));
      }
    }
    void send() { if (!scheduled.isEmpty()) scheduled.remove().run(); }
    void leadingFeedback() {
      Teleport head = movement.pendingTeleports.get().peekFirst();
      if (head != null) controller.beforeTeleportTransactionReceive(user, head);
    }
    void ack() {
      Teleport head = movement.pendingTeleports.get().peekFirst();
      if (head != null) {
        movement.sentTeleportIdBefore = true;
        movement.lastTeleportAcceptId = head.id().orElse(0);
      }
    }
    void confirm() {
      Teleport head = movement.pendingTeleports.get().peekFirst();
      if (head != null && controller.confirmTeleport(user, head.change().position(), Rotation.zero())) {
        serverPosition = Position.mutableCopy(movement.verifiedLastPosition());
      }
    }
    void finish() {
      while (!scheduled.isEmpty()) send();
      int remaining = 10;
      while (!movement.pendingTeleports.get().isEmpty() && remaining-- > 0) { leadingFeedback(); ack(); confirm(); }
      assertTrue(movement.pendingTeleports.get().isEmpty());
    }
  }
}

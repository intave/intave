package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PendingTeleportRecoveryTest {
  private static final Position BEFORE = new Position(2, 65, 4);
  private static final Position DESTINATION = new Position(2, 75, 4);
  private static final Position ILLEGAL = new Position(100, 200, 300);
  private static final Motion BEFORE_MOTION = new Motion(0.125, 0.25, 0.125);
  private static final Motion AFTER_MOTION = new Motion(0.125, 0.5, 0.125);

  @Test void invalidLegacyStreamPacketCannotReplaceServerTeleportWithOldPosition() {
    Scenario s = new Scenario(47);
    s.rejectMovement(true);
    for (int tick = 0; tick < 40; tick++) s.assertBlockedTick();
    s.controller.teleport(s.user, PositionMoveRotation.withoutRotation(ILLEGAL, BEFORE_MOTION), Relative.RELATIVE_ROTATION);
    assertTrue(s.scheduled.isEmpty());
    assertTrue(s.transmitted.isEmpty());
    assertSame(s.original, s.head());
    s.finish();
  }

  @Test void bruteForceFiveEventsAfterInvalidInFlightMovement() {
    // 8^5 orderings for each protocol: 65,536 histories total. Packet transport
    // and a valid tick's physics result are modeled; recovery/confirmation and
    // dispatcher commit predicates are the production implementations.
    for (int protocol : new int[] {47, 774}) {
      for (int history = 0; history < 32768; history++) {
        try {
          Scenario s = new Scenario(protocol);
          s.rejectMovement((history & 1) == 0);
          int events = history;
          for (int step = 0; step < 5 && s.movement.inRecovery; step++, events /= 8) {
            switch (events % 8) {
              case 0: s.assertBlockedTick(); break;
              case 1:
                s.controller.movementCorrection(s.user, PositionMoveRotation.withoutRotation(ILLEGAL, BEFORE_MOTION));
                s.controller.teleport(s.user, PositionMoveRotation.withoutRotation(ILLEGAL, BEFORE_MOTION), Relative.RELATIVE_ROTATION);
                break;
              case 2: s.controller.onResendTimeout(s.user); break;
              case 3: s.controller.beforeTeleportTransactionReceive(s.user, s.head()); break;
              case 4: s.ack(); break;
              case 5: s.confirm(); break;
              case 6:
                // Once retired, neither callback may affect the replacement.
                s.controller.beforeTeleportTransactionReceive(s.user, s.original);
                s.controller.afterTeleportTransactionReceive(s.user, s.original);
                break;
              case 7:
                s.movement.sentTeleportIdBefore = true;
                s.movement.lastTeleportAcceptId = 999;
                assertFalse(s.controller.confirmTeleport(s.user, ILLEGAL, Rotation.zero()));
                break;
              default: throw new AssertionError();
            }
            assertTrue(s.scheduled.isEmpty(), "must not schedule an old-position correction");
            assertEquals(1, s.movement.teleportSequence, "retries must retain the logical request");
            if (s.movement.inRecovery) {
              s.assertBlockedTick();
              assertEquals(BEFORE, s.movement.verifiedLastPosition());
              assertEquals(BEFORE_MOTION, s.movement.mutableBaseMotionCopy());
              assertEquals(1, s.movement.pendingTeleports.get().size());
            }
          }
          s.finish();
        } catch (AssertionError failure) {
          throw new AssertionError("protocol=" + protocol + ", history=" + history + " (base-8 events, least significant first)", failure);
        }
      }
    }
  }

  private static final class Scenario {
    final User user;
    final MovementMetadata movement;
    final List<Runnable> scheduled = new ArrayList<>();
    final List<Teleport> transmitted = new ArrayList<>();
    final TeleportController controller = new TeleportController((u, work) -> scheduled.add(work), (u, packet) -> transmitted.add(packet));
    final Teleport original;

    Scenario(int protocol) {
      MinecraftVersion.setCurrent(protocol == 47 ? MinecraftVersions.VER1_8_0 : MinecraftVersions.VER1_21_4);
      user = UserFactory.createFallback();
      user.meta().protocol().setProtocolVersion(protocol);
      movement = user.meta().movement();
      // Model an outgoing server request rather than an Intave correction.
      original = new Teleport(movement.teleportSequence++, protocol == 47 ? OptionalInt.empty() : OptionalInt.of(42),
        new PositionMoveRotation(new Position(0, 10, 0), new Motion(0, 0.25, 0), new Rotation(15, 5)),
        EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.DELTA_X, Relative.DELTA_Y,
          Relative.DELTA_Z, Relative.Y_ROT, Relative.X_ROT));
      movement.pendingTeleports.get().add(original);
      assertTrue(controller.processMovementPackets(user));
      assertFalse(controller.forwardMovementPackets(user));
      movement.movementWithheldForTeleport = true;
      assertTrue(MovementDispatcher.canCommitMovement(movement, true));
      movement.setPosition(BEFORE);
      movement.setVerifiedLastPosition(BEFORE, "valid pre-teleport tick");
      movement.setBaseMotion(BEFORE_MOTION.copy());
      movement.setLastRotation(new Rotation(30, 10));
    }

    void rejectMovement(boolean throughCorrection) {
      movement.setPosition(ILLEGAL);
      movement.invalidMovement = true;
      if (throughCorrection) controller.movementCorrection(user, PositionMoveRotation.withoutRotation(ILLEGAL, BEFORE_MOTION));
      else movement.recoverPendingTeleport();
      assertTrue(movement.inRecovery);
      assertFalse(MovementDispatcher.canCommitMovement(movement, true));
    }

    void assertBlockedTick() {
      // The latch must survive clearing the transient flags between packets.
      movement.invalidMovement = false;
      movement.dropPostTickMotionProcessing = false;
      movement.movementWithheldForTeleport = true;
      assertFalse(controller.processMovementPackets(user));
      assertFalse(controller.forwardMovementPackets(user));
      assertFalse(MovementDispatcher.canCommitMovement(movement, true));
      assertFalse(MovementDispatcher.canCommitMovement(movement, false));
    }

    Teleport head() { return movement.pendingTeleports.get().peekFirst(); }
    void ack() {
      movement.sentTeleportIdBefore = true;
      movement.lastTeleportAcceptId = head().id().orElse(0);
    }
    void confirm() { controller.confirmTeleport(user, DESTINATION, new Rotation(45, 15)); }
    void finish() {
      if (head() != null) {
        controller.beforeTeleportTransactionReceive(user, head());
        ack();
        confirm();
      }
      assertTrue(movement.pendingTeleports.get().isEmpty());
      assertFalse(movement.inRecovery);
      assertTrue(controller.processMovementPackets(user));
      assertTrue(controller.forwardMovementPackets(user));
      assertEquals(DESTINATION, movement.verifiedLastPosition());
      assertEquals(AFTER_MOTION, movement.mutableBaseMotionCopy());
      assertEquals(new Rotation(45, 15), movement.rotation());
    }
  }
}

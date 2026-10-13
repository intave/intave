package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.LONG_TELEPORT;
import static de.jpx3.intave.check.movement.physics.environment.MoveMetric.TELEPORT;
import static org.junit.jupiter.api.Assertions.*;

class TeleportConfirmationTest {
  private static final int[] ACKNOWLEDGEMENT_FORMATS = {47, 774, ProtocolMetadata.VER_26_3};
  private static final Position TARGET = new Position(100, 70, 0);
  private final TeleportController controller = new TeleportController((u, task) -> task.run(), (u, tp) -> tp.allow());
  private User user;
  private MovementMetadata movement;

  private void setup(int protocolVersion) {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(protocolVersion);
    movement = user.meta().movement();
    movement.setVerifiedLastPosition(new Position(0, 64, 0), "test baseline");
    movement.setBaseMotion(Motion.newEmpty());
    movement.setLastRotation(0, 0);
    movement.setRotation(60, 10);
    movement.setPast(TELEPORT, 100);
    movement.setPast(LONG_TELEPORT, 100);
  }

  @Test
  void confirmationResetsTeleportMetricsAndRotationBaseline() {
    for (int protocolVersion : ACKNOWLEDGEMENT_FORMATS) {
      setup(protocolVersion);
      controller.teleport(user, PositionMoveRotation.withoutMotion(TARGET, new Rotation(270, 20)), EnumSet.noneOf(Relative.class));
      confirm(new Rotation(270, 20));
      assertEquals(0, movement.ticksPast(TELEPORT));
      assertEquals(0, movement.ticksPast(LONG_TELEPORT));
      // The server-induced turn must not show up as a rotation delta
      assertEquals(new Rotation(270, 20), movement.rotation());
      assertEquals(new Rotation(270, 20), movement.lastRotation());
    }
  }

  @Test
  void relativeCorrectionKeepsTheClientsView() {
    for (int protocolVersion : ACKNOWLEDGEMENT_FORMATS) {
      setup(protocolVersion);
      controller.movementCorrection(user, PositionMoveRotation.withoutRotation(TARGET, Motion.newEmpty()));
      // The client kept turning while the correction was in flight
      confirm(new Rotation(75, 15));
      assertEquals(new Rotation(75, 15), movement.rotation());
      assertEquals(new Rotation(75, 15), movement.lastRotation());
    }
  }

  @Test
  void confirmingMovementPacketDoesNotAgeTeleportMetric() {
    setup(774);
    controller.teleport(user, PositionMoveRotation.withoutMotion(TARGET, new Rotation(270, 20)), EnumSet.noneOf(Relative.class));
    confirm(new Rotation(270, 20));
    movement.isTeleportConfirmationPacket = true;
    movement.tickComplete(true, true, true);
    assertEquals(0, movement.ticksPast(TELEPORT));
    movement.tickComplete(true, true, true);
    assertEquals(1, movement.ticksPast(TELEPORT));
  }

  @Test
  void rejectsNonFiniteYawInCombinedAccept() {
    for (float yaw : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      assertRejectedCombinedAccept(new PositionAndRotation(TARGET, new Rotation(yaw, 20)));
    }
  }

  @Test
  void rejectsNonFinitePitchInCombinedAccept() {
    for (float pitch : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
      assertRejectedCombinedAccept(new PositionAndRotation(TARGET, new Rotation(90, pitch)));
    }
  }

  @Test
  void rejectsNonFinitePositionInCombinedAccept() {
    for (double coordinate : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertRejectedCombinedAccept(new PositionAndRotation(coordinate, TARGET.getY(), TARGET.getZ(), 90, 20));
      assertRejectedCombinedAccept(new PositionAndRotation(TARGET.getX(), coordinate, TARGET.getZ(), 90, 20));
      assertRejectedCombinedAccept(new PositionAndRotation(TARGET.getX(), TARGET.getY(), coordinate, 90, 20));
    }
  }

  private void assertRejectedCombinedAccept(PositionAndRotation acceptedState) {
    setup(ProtocolMetadata.VER_26_3);
    Position previousPosition = movement.verifiedLastPosition();
    int previousTeleportId = movement.lastTeleportAcceptId;
    controller.movementCorrection(user, PositionMoveRotation.withoutRotation(TARGET, Motion.newEmpty()));
    Teleport pending = movement.pendingTeleports.get().peekFirst();

    controller.receiveTeleportAccept(user, pending.id().getAsInt(), acceptedState);

    assertFalse(pending.wasAccepted(), acceptedState.toString());
    assertSame(pending, movement.pendingTeleports.get().peekFirst());
    assertTrue(movement.inRecovery);
    assertFalse(movement.sentTeleportIdBefore);
    assertEquals(previousTeleportId, movement.lastTeleportAcceptId);
    assertEquals(previousPosition, movement.verifiedLastPosition());
    assertEquals(new Rotation(60, 10), movement.rotation());
    assertEquals(Rotation.zero(), movement.lastRotation());
    assertEquals(100, movement.ticksPast(TELEPORT));
    assertEquals(100, movement.ticksPast(LONG_TELEPORT));

    // A malformed response must leave the pending correction available to confirm.
    confirm(new Rotation(75, 15));
    assertFalse(movement.inRecovery);
    assertEquals(new Rotation(75, 15), movement.rotation());
    assertEquals(new Rotation(75, 15), movement.lastRotation());
    assertEquals(0, movement.ticksPast(TELEPORT));
    assertEquals(0, movement.ticksPast(LONG_TELEPORT));
  }

  @Test
  void twentyBlockTeleportDoesNotResetLongTeleportMetric() {
    setup(ProtocolMetadata.VER_26_3);
    Position nearby = new Position(20, 70, 0);
    controller.teleport(user, PositionMoveRotation.withoutMotion(nearby, Rotation.zero()), EnumSet.noneOf(Relative.class));
    Teleport pending = movement.pendingTeleports.get().peekFirst();
    controller.receiveTeleportAccept(user, pending.id().getAsInt(), new PositionAndRotation(nearby, Rotation.zero()));
    assertTrue(pending.wasAccepted());
    assertEquals(0, movement.ticksPast(TELEPORT));
    assertEquals(100, movement.ticksPast(LONG_TELEPORT));
  }

  @Test
  void mismatchedAcknowledgementDoesNotResetMetricsOrRotation() {
    setup(ProtocolMetadata.VER_26_3);
    controller.teleport(user, PositionMoveRotation.withoutMotion(TARGET, Rotation.zero()), EnumSet.noneOf(Relative.class));
    Teleport pending = movement.pendingTeleports.get().peekFirst();
    controller.receiveTeleportAccept(user, pending.id().getAsInt() + 1, new PositionAndRotation(TARGET, Rotation.zero()));
    assertFalse(pending.wasAccepted());
    assertSame(pending, movement.pendingTeleports.get().peekFirst());
    assertEquals(100, movement.ticksPast(TELEPORT));
    assertEquals(100, movement.ticksPast(LONG_TELEPORT));
    assertEquals(new Rotation(60, 10), movement.rotation());
  }

  @Test
  void idOnlyAcknowledgementStillWaitsForMovementConfirmation() {
    // Older server runtimes may expose only the ID, even for translated modern clients.
    for (int protocolVersion : new int[] {774, ProtocolMetadata.VER_26_3}) {
      setup(protocolVersion);
      controller.teleport(user, PositionMoveRotation.withoutMotion(TARGET, Rotation.zero()), EnumSet.noneOf(Relative.class));
      Teleport pending = movement.pendingTeleports.get().peekFirst();
      controller.receiveTeleportAccept(user, pending.id().getAsInt(), null);
      assertFalse(pending.wasAccepted());
      assertSame(pending, movement.pendingTeleports.get().peekFirst());
      assertTrue(movement.sentTeleportIdBefore);
      assertEquals(pending.id().getAsInt(), movement.lastTeleportAcceptId);
      assertTrue(controller.confirmTeleport(user, TARGET, Rotation.zero()));
      assertTrue(movement.pendingTeleports.get().isEmpty());
    }
  }

  private void confirm(Rotation rotation) {
    Teleport pending = movement.pendingTeleports.get().peekFirst();
    int id = pending.id().orElse(0);
    if (user.meta().protocol().teleportAcceptIncludesPositionAndRotation()) {
      controller.receiveTeleportAccept(user, id,
        new PositionAndRotation(TARGET.getX(), TARGET.getY(), TARGET.getZ(), rotation.yaw(), rotation.pitch()));
    } else {
      movement.sentTeleportIdBefore = true;
      movement.lastTeleportAcceptId = id;
      assertTrue(controller.confirmTeleport(user, TARGET, rotation));
    }
    assertTrue(movement.pendingTeleports.get().isEmpty());
  }
}

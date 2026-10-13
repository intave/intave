package de.jpx3.intave.module.dispatch;

import ac.intave.samples.event.TeleportEvent;
import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.*;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TeleportRetryOrderingTest {
  private User user;
  private MovementMetadata movement;
  private final List<Teleport> sent = new ArrayList<>();
  private final TeleportController controller = new TeleportController((u, task) -> task.run(), (u, tp) -> sent.add(tp));

  @BeforeEach void setup() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(774);
    movement = user.meta().movement();
    movement.setVerifiedLastPosition(new Position(0, 64, 0), "retry baseline");
    movement.setBaseMotion(Motion.newEmpty());
  }

  @Test void olderCorrectionCannotOverrideNewerDestinationOrFinishItsRecovery() {
    controller.movementCorrection(user, new MovementCorrection(change(100, 1), false));
    Teleport old = sent.get(0);
    serverTeleport(change(64, 0), EnumSet.noneOf(Relative.class));
    Teleport newer = sent.get(1);
    long sequence = movement.teleportSequence;
    controller.onResendTimeout(user);
    Teleport retry = sent.get(2);
    assertEquals(newer.uniqueId(), retry.uniqueId());
    assertEquals(newer.id(), retry.id());
    assertEquals(sequence, movement.teleportSequence);
    assertEquals(1, movement.pendingTeleports.get().size());
    assertEquals(64, retry.change().position().getY());
    assertEquals(0, retry.change().motion().motionY());
    assertFalse(confirm(old));
    controller.beforeTeleportTransactionReceive(user, old);
    controller.afterTeleportTransactionReceive(user, newer);
    assertEquals(3, sent.size());
    assertTrue(movement.inRecovery);
    assertFalse(retry.isAllowed());
    assertFalse(confirm(retry));
    controller.beforeTeleportTransactionReceive(user, retry);
    assertTrue(confirm(retry));
    assertFalse(movement.inRecovery);
    assertEquals(64, movement.verifiedLastPosition().getY());
    assertFalse(confirm(old));
  }

  @Test void repeatedRetriesApplyRelativePositionAndAdditiveMotionOnlyOnce() {
    controller.teleport(user, change(10, 0.5), EnumSet.of(Relative.Y, Relative.DELTA_Y));
    serverTeleport(change(5, 0.25), EnumSet.of(Relative.Y, Relative.DELTA_Y));
    long newest = sent.get(1).uniqueId();
    for (int attempt = 0; attempt < 20; attempt++) {
      Teleport previous = sent.get(sent.size() - 1);
      controller.onResendTimeout(user);
      Teleport retry = sent.get(sent.size() - 1);
      assertEquals(newest, retry.uniqueId());
      assertEquals(79, retry.change().position().getY());
      assertEquals(0.75, retry.change().motion().motionY());
      assertTrue(retry.relativeSet().isEmpty());
      assertEquals(previous.id(), retry.id());
      int count = sent.size();
      controller.afterTeleportTransactionReceive(user, previous);
      assertEquals(count, sent.size());
    }
    Teleport finalRetry = sent.get(sent.size() - 1);
    assertFalse(confirm(finalRetry));
    controller.beforeTeleportTransactionReceive(user, finalRetry);
    assertTrue(confirm(finalRetry));
    assertEquals(79, movement.verifiedLastPosition().getY());
    assertEquals(0.75, movement.mutableBaseMotionCopy().motionY());
  }

  @Test void invalidMovementWithoutCorrectionWaitsForNewestPendingTeleportIncludingRetry() {
    serverTeleport(change(70, 0), EnumSet.noneOf(Relative.class));
    serverTeleport(change(80, 0), EnumSet.noneOf(Relative.class));
    Teleport first = sent.get(0);
    movement.invalidMovement = true;
    movement.recoverPendingTeleport();
    assertFalse(controller.processMovementPackets(user));
    controller.beforeTeleportTransactionReceive(user, first);
    assertTrue(confirm(first));
    assertTrue(movement.inRecovery);
    controller.onResendTimeout(user);
    Teleport retry = sent.get(2);
    movement.invalidMovement = false;
    movement.dropPostTickMotionProcessing = false;
    assertFalse(controller.processMovementPackets(user));
    controller.beforeTeleportTransactionReceive(user, retry);
    assertTrue(confirm(retry));
    assertFalse(movement.inRecovery);
    assertTrue(controller.processMovementPackets(user));
    assertEquals(80, movement.verifiedLastPosition().getY());
  }

  @Test void legacyRetryUsesAbsoluteVelocityAndIgnoresOldFeedbackWithoutIds() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_8_0);
    user.meta().protocol().setProtocolVersion(47);
    controller.teleport(user, change(10, 0.5), EnumSet.of(Relative.Y, Relative.DELTA_Y));
    controller.onResendTimeout(user);
    Teleport previous = sent.get(1);
    assertFalse(previous.additiveMotionPacket());
    assertFalse(previous.id().isPresent());
    controller.onResendTimeout(user);
    Teleport retry = sent.get(2);
    controller.afterTeleportTransactionReceive(user, previous);
    assertEquals(3, sent.size());
    controller.beforeTeleportTransactionReceive(user, previous);
    assertFalse(retry.isAllowed());
    assertFalse(confirm(retry));
    controller.beforeTeleportTransactionReceive(user, retry);
    assertTrue(retry.isAllowed());
    assertTrue(confirm(retry));
    assertEquals(74, movement.verifiedLastPosition().getY());
    assertEquals(0.5, movement.mutableBaseMotionCopy().motionY());
  }

  @Test void emitsAbsoluteTeleportOnlyAfterFeedbackAcceptAndMatchingMovement() {
    List<Teleport> transmitted = new ArrayList<>();
    List<TeleportEvent> emitted = new ArrayList<>();
    TeleportController emittingController = new TeleportController(
      (u, task) -> task.run(),
      (u, teleport) -> transmitted.add(teleport),
      (u, event) -> emitted.add(event)
    );

    emittingController.teleport(user, change(10, 0), EnumSet.of(Relative.Y));
    Teleport teleport = transmitted.get(0);
    Position responsePosition = new Position(0, 74, 0);

    assertFalse(emittingController.confirmTeleport(user, responsePosition, Rotation.zero()));
    assertTrue(emitted.isEmpty());

    emittingController.beforeTeleportTransactionReceive(user, teleport);
    movement.sentTeleportIdBefore = true;
    movement.lastTeleportAcceptId = teleport.id().getAsInt() + 1;
    assertFalse(emittingController.confirmTeleport(user, responsePosition, Rotation.zero()));
    assertTrue(emitted.isEmpty());

    movement.lastTeleportAcceptId = teleport.id().getAsInt();
    assertFalse(emittingController.confirmTeleport(user, new Position(0, 75, 0), Rotation.zero()));
    assertTrue(emitted.isEmpty());

    assertTrue(emittingController.confirmTeleport(user, responsePosition, Rotation.zero()));
    assertEquals(1, emitted.size());
    TeleportEvent event = emitted.get(0);
    assertEquals(new ac.intave.samples.share.Position(0, 74, 0), event.position());
    assertEquals(ac.intave.samples.share.Rotation.ZERO, event.rotation());
    assertEquals(teleport.id().getAsInt(), event.teleportId());

    assertFalse(emittingController.confirmTeleport(user, responsePosition, Rotation.zero()));
    assertEquals(1, emitted.size());
  }

  @Test void minecraft263CombinedAcceptCompletesTeleportWithoutAnotherMovementResponse() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_26_3);
    controller.teleport(user, change(5, 0), EnumSet.of(Relative.Y));
    Teleport teleport = sent.get(0);
    controller.beforeTeleportTransactionReceive(user, teleport);

    controller.receiveTeleportAccept(
      user,
      teleport.id().getAsInt(),
      new PositionAndRotation(0, 69, 0, 0, 0)
    );

    assertTrue(teleport.wasAccepted());
    assertTrue(movement.pendingTeleports.get().isEmpty());
    assertEquals(new Position(0, 69, 0), movement.verifiedLastPosition());
    assertFalse(controller.confirmTeleport(user, new Position(0, 68.995, 0), Rotation.zero()));
  }

  @Test void pre263AcceptStillWaitsForSeparateMovementResponse() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
    user.meta().protocol().setProtocolVersion(ProtocolMetadata.VER_26_2);
    controller.teleport(user, change(5, 0), EnumSet.of(Relative.Y));
    Teleport teleport = sent.get(0);
    controller.beforeTeleportTransactionReceive(user, teleport);

    controller.receiveTeleportAccept(
      user,
      teleport.id().getAsInt(),
      new PositionAndRotation(0, 69, 0, 0, 0)
    );

    assertFalse(teleport.wasAccepted());
    assertEquals(teleport, movement.pendingTeleports.get().peekFirst());
    assertTrue(controller.confirmTeleport(user, new Position(0, 69, 0), Rotation.zero()));
  }

  private PositionMoveRotation change(double y, double velocityY) {
    return PositionMoveRotation.withoutRotation(new Position(0, y, 0), new Motion(0, velocityY, 0));
  }

  // Model an independent server request, which is allowed to supersede Intave.
  private void serverTeleport(PositionMoveRotation change, Set<Relative> flags) {
    movement.replaceRecoveryWithExternalTeleport();
    Teleport teleport = new Teleport(movement.teleportSequence++, OptionalInt.of(42), change, flags);
    movement.pendingTeleports.get().add(teleport);
    sent.add(teleport);
  }

  private boolean confirm(Teleport teleport) {
    movement.sentTeleportIdBefore = true;
    movement.lastTeleportAcceptId = teleport.id().orElse(0);
    return controller.confirmTeleport(user, teleport.change().position(), Rotation.zero());
  }
}

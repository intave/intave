package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionAndRotation;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.share.Teleport;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class TeleportAcknowledgementForwardingTest {
  private static final int[] PROTOCOLS = {774, ProtocolMetadata.VER_26_3};

  @Test
  void mountedPositionMismatchCanRecoverWithoutAcknowledgingTheServerTwice() {
    for (int protocol : PROTOCOLS) {
      Fixture f = new Fixture(protocol);
      Teleport original = f.serverTeleport(42, 70);
      f.movement.invalidMovement = true;
      f.movement.recoverPendingTeleport();

      // A mounted client acknowledges the ID but reports its unchanged position.
      assertTrue(f.respond(original, 64));
      assertFalse(original.wasAccepted());
      assertTrue(f.movement.inRecovery);
      f.controller.afterTeleportTransactionReceive(f.user, original);

      Teleport retry = f.sent.get(0);
      assertEquals(original.id(), retry.id());
      f.controller.beforeTeleportTransactionReceive(f.user, retry);
      assertFalse(f.respond(retry, 70));
      assertTrue(retry.wasAccepted());
      assertFalse(f.movement.inRecovery);
      assertTrue(f.movement.pendingTeleports.get().isEmpty());
      assertEquals(new Position(0, 70, 0), f.movement.verifiedLastPosition());
      // Protection survives removal from Intave's pending queue.
      assertFalse(f.respond(retry, 70));
      assertEquals(1, f.serverAccepts);
    }
  }

  @Test
  void retryBeforeTheFirstAcknowledgementStillForwardsExactlyOne() {
    Fixture f = new Fixture(774);
    Teleport original = f.serverTeleport(42, 70);
    f.controller.onResendTimeout(f.user);
    Teleport retry = f.sent.get(0);

    assertTrue(f.respond(original, 64));
    f.controller.beforeTeleportTransactionReceive(f.user, retry);
    assertFalse(f.respond(retry, 70));
    assertTrue(retry.wasAccepted());
    assertEquals(1, f.serverAccepts);
  }

  @Test
  void repeatedRetriesDoNotResetTheServersAcknowledgement() {
    Fixture f = new Fixture(774);
    Teleport original = f.serverTeleport(42, 70);
    assertTrue(f.respond(original, 64));
    for (int attempt = 0; attempt < 3; attempt++) {
      f.controller.onResendTimeout(f.user);
      Teleport retry = f.sent.get(attempt);
      f.controller.beforeTeleportTransactionReceive(f.user, retry);
      assertFalse(f.respond(retry, attempt == 2 ? 70 : 64));
    }
    assertTrue(f.movement.pendingTeleports.get().isEmpty());
    assertEquals(1, f.serverAccepts);
  }

  @Test
  void intaveCorrectionDoesNotEraseThePreviousServerAcknowledgement() {
    Fixture f = new Fixture(774);
    Teleport nativeTeleport = f.serverTeleport(42, 70);
    assertTrue(f.respond(nativeTeleport, 70));

    f.controller.teleport(f.user, change(80), EnumSet.noneOf(Relative.class));
    Teleport correction = f.sent.get(0);
    assertTrue(correction.id().getAsInt() < 0);
    f.controller.beforeTeleportTransactionReceive(f.user, correction);
    assertTrue(f.respond(correction, 80));
    assertTrue(correction.wasAccepted());
    assertFalse(f.respond(nativeTeleport, 70));
    assertEquals(1, f.serverAccepts);
  }

  @Test
  void newerServerTeleportAcceptsItsFirstResponseDespiteOlderResponses() {
    Fixture f = new Fixture(774);
    Teleport older = f.serverTeleport(42, 70);
    Teleport newer = f.serverTeleport(43, 80);

    assertTrue(f.respond(older, 70));
    assertEquals(0, f.serverAccepts);
    assertTrue(f.respond(newer, 80));
    assertTrue(newer.wasAccepted());
    assertFalse(f.respond(newer, 80));
    assertEquals(1, f.serverAccepts);

    // A new native request may reuse an ID after the server's counter wraps.
    Teleport reused = f.serverTeleport(42, 90);
    assertTrue(f.respond(reused, 90));
    assertFalse(f.respond(reused, 90));
    assertEquals(2, f.serverAccepts);
  }

  @Test
  void invalidCombinedAcknowledgementDoesNotConsumeTheFirstValidResponse() {
    Fixture f = new Fixture(ProtocolMetadata.VER_26_3);
    Teleport original = f.serverTeleport(42, 70);
    assertFalse(f.controller.receiveTeleportAccept(f.user, 42,
      new PositionAndRotation(Double.NaN, 70, 0, 0, 0)));
    assertFalse(f.movement.sentTeleportIdBefore);
    assertFalse(original.wasAccepted());
    assertTrue(f.respond(original, 70));
    assertEquals(1, f.serverAccepts);
  }

  @Test
  void unknownIdsAndPlayersWithoutNativeIdsRemainIndependent() {
    Fixture first = new Fixture(774);
    Fixture second = new Fixture(774);
    Teleport firstTeleport = first.serverTeleport(42, 70);
    Teleport secondTeleport = second.serverTeleport(42, 70);
    assertTrue(first.controller.receiveTeleportAccept(first.user, 99, null));
    assertTrue(first.respond(firstTeleport, 70));
    assertTrue(second.respond(secondTeleport, 70));
    assertFalse(first.respond(firstTeleport, 70));
    assertFalse(second.respond(secondTeleport, 70));

    first.movement.expectServerTeleport(OptionalInt.empty());
    assertTrue(first.controller.receiveTeleportAccept(first.user, 42, null));
  }

  private static PositionMoveRotation change(double y) {
    return PositionMoveRotation.withoutRotation(new Position(0, y, 0), Motion.newEmpty());
  }

  private static final class Fixture {
    final User user;
    final MovementMetadata movement;
    final List<Teleport> sent = new ArrayList<>();
    final TeleportController controller = new TeleportController((u, task) -> task.run(), (u, tp) -> sent.add(tp));
    final boolean combined;
    int serverId;
    boolean serverAwaitingPosition;
    int serverAccepts;

    Fixture(int protocol) {
      combined = protocol == ProtocolMetadata.VER_26_3;
      MinecraftVersion.setCurrent(new MinecraftVersion(combined ? "26.3" : "1.21.11"));
      user = UserFactory.createFallback();
      user.meta().protocol().setProtocolVersion(protocol);
      movement = user.meta().movement();
      movement.setVerifiedLastPosition(new Position(0, 64, 0), "teleport acknowledgement baseline");
      movement.setBaseMotion(Motion.newEmpty());
    }

    Teleport serverTeleport(int id, double y) {
      serverId = id;
      serverAwaitingPosition = true;
      movement.expectServerTeleport(OptionalInt.of(id));
      movement.replaceRecoveryWithExternalTeleport();
      Teleport teleport = new Teleport(movement.teleportSequence++, OptionalInt.of(id), change(y), EnumSet.noneOf(Relative.class));
      movement.pendingTeleports.get().add(teleport);
      controller.beforeTeleportTransactionReceive(user, teleport);
      return teleport;
    }

    boolean respond(Teleport teleport, double y) {
      int id = teleport.id().getAsInt();
      Position position = new Position(0, y, 0);
      boolean forward = controller.receiveTeleportAccept(user, id,
        combined ? new PositionAndRotation(position, Rotation.zero()) : null);
      // ServerGamePacketListenerImpl disconnects when its latest ID is accepted
      // a second time, after awaitingPositionFromClient has been cleared.
      if (forward && id == serverId) {
        assertTrue(serverAwaitingPosition, "duplicate native acknowledgement would disconnect the player");
        serverAwaitingPosition = false;
        serverAccepts++;
      }
      if (!combined) controller.confirmTeleport(user, position, Rotation.zero());
      return forward;
    }
  }
}

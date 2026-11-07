package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.entity.type.EntityTypeData;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.*;
import de.jpx3.intave.test.FakePlayerFactory;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MountedCorrectionTest {
  private static final Position DISMOUNT = new Position(2, 64, 3);

  @Test
  void nativeDismountTeleportCompletesCorrectionWithoutSendingVehicleCoordinates() {
    Scenario s = new Scenario();
    s.correct();
    assertFalse(s.mounted);
    assertTrue(s.transmitted.isEmpty());
    assertEquals(DISMOUNT, s.movement.pendingTeleports.get().getFirst().change().position());
    s.confirm();
  }

  @Test
  void ignoredMountedTeleportIsReplacedByTheNativeDismountDestination() {
    Scenario s = new Scenario();
    s.nativeTeleport(new Position(20, 70, 20));
    s.correct();
    assertEquals(1, s.transmitted.size());
    assertEquals(1, s.movement.pendingTeleports.get().size());
    assertEquals(DISMOUNT, s.transmitted.getFirst().change().position());
    s.confirm();
  }

  @Test
  void existingRecoverySchedulesOnlyOneDismount() {
    Scenario s = new Scenario();
    s.nativeTeleport(new Position(20, 70, 20));
    s.movement.invalidMovement = true;
    s.movement.recoverPendingTeleport();
    s.requestCorrection();
    s.requestCorrection();
    s.controller.onResendTimeout(s.user);
    assertEquals(1, s.scheduled.size());
    assertFalse(s.controller.processMovementPackets(s.user));
    s.scheduled.removeFirst().run();
    assertFalse(s.mounted);
    assertEquals(1, s.movement.pendingTeleports.get().size());
    s.confirm();
  }

  @Test
  void correctionChecksNativeMountBeforeFeedbackArrives() {
    for (boolean pending : new boolean[]{false, true}) {
      Scenario s = new Scenario();
      s.movement.restoreRecordedVehicle(null);
      if (pending) s.nativeTeleport(new Position(20, 70, 20));
      s.correct();
      assertFalse(s.mounted);
      assertEquals(DISMOUNT, s.movement.pendingTeleports.get().getFirst().change().position());
      s.confirm();
    }
  }

  @Test
  void retryRecoversNativeMountWithoutAnotherCorrection() {
    Scenario s = new Scenario();
    s.movement.restoreRecordedVehicle(null);
    s.nativeTeleport(new Position(20, 70, 20));
    s.movement.invalidMovement = true;
    s.movement.recoverPendingTeleport();
    s.controller.onResendTimeout(s.user);
    assertEquals(1, s.scheduled.size());
    s.scheduled.removeFirst().run();
    assertFalse(s.mounted);
    s.confirm();
  }

  @Test
  void cancelledDismountDoesNotSendAnUnconfirmablePlayerTeleport() {
    for (boolean pending : new boolean[]{false, true}) {
      Scenario s = new Scenario();
      if (pending) s.nativeTeleport(new Position(20, 70, 20));
      Teleport original = s.movement.pendingTeleports.get().peekLast();
      s.cancelDismount = true;
      s.correct();
      assertTrue(s.mounted);
      assertFalse(s.movement.inRecovery);
      assertTrue(s.movement.invalidMovement);
      assertEquals(pending ? 1 : 0, s.movement.pendingTeleports.get().size());
      assertSame(original, s.movement.pendingTeleports.get().peekLast());
      assertTrue(s.transmitted.isEmpty());
    }
  }

  @Test
  void newerExternalTeleportSupersedesTheScheduledDismount() {
    Scenario s = new Scenario();
    s.controller.movementCorrection(s.user, PositionMoveRotation.withoutRotation(Position.immutableEmpty(), Motion.newEmpty()));
    s.nativeTeleport(DISMOUNT);
    s.scheduled.removeFirst().run();
    assertTrue(s.mounted);
    s.confirm();
  }

  @Test
  void completedTeleportRetiresItsQueuedRecovery() {
    Scenario s = new Scenario();
    s.mounted = false;
    s.movement.restoreRecordedVehicle(null);
    s.nativeTeleport(DISMOUNT);
    s.movement.invalidMovement = true;
    s.movement.recoverPendingTeleport();
    s.requestCorrection();
    s.confirm();
    s.scheduled.removeFirst().run();
    assertTrue(s.transmitted.isEmpty());
    assertFalse(s.movement.inRecovery);
  }

  @Test
  void staleTaskCannotReleaseANewerRecoveryTask() {
    Scenario s = new Scenario();
    s.requestCorrection();
    s.nativeTeleport(DISMOUNT);
    s.mounted = false;
    s.movement.restoreRecordedVehicle(null);
    s.controller.onResendTimeout(s.user);
    assertEquals(2, s.scheduled.size());
    s.scheduled.removeFirst().run();
    s.controller.onResendTimeout(s.user);
    assertEquals(1, s.scheduled.size());
    s.scheduled.removeFirst().run();
    assertEquals(1, s.transmitted.size());
    assertEquals(DISMOUNT, s.transmitted.getFirst().change().position());
    s.confirm();
  }

  private static final class Scenario {
    final List<Runnable> scheduled = new ArrayList<>();
    final List<Teleport> transmitted = new ArrayList<>();
    final TeleportController controller = new TeleportController((u, work) -> scheduled.add(work), (u, packet) -> transmitted.add(packet));
    final User user;
    final MovementMetadata movement;
    boolean mounted = true;
    boolean cancelDismount;

    Scenario() {
      MinecraftVersion.setCurrent(MinecraftVersions.VER26_3);
      Player player = FakePlayerFactory.createPlayer((method, arguments) -> {
        if (method.equals("isInsideVehicle")) return mounted;
        if (method.equals("getLocation")) return new Location(null, 2, 64, 3);
        if (method.equals("leaveVehicle")) {
          if (!cancelDismount) {
            mounted = false;
            nativeTeleport(DISMOUNT);
          }
          return true;
        }
        return null;
      });
      user = UserFactory.createTestUserFor(player, ProtocolMetadata.VER_26_3);
      UserRepository.manuallyRegisterUser(player, user);
      movement = user.meta().movement();
      movement.restoreRecordedVehicle(new Entity(5, new EntityTypeData("horse", HitboxSize.of(1.3964844F, 1.6F), 5, true, 11), false));
    }

    void nativeTeleport(Position position) {
      movement.replaceRecoveryWithExternalTeleport();
      long sequence = movement.teleportSequence++;
      Teleport teleport = Teleport.of(sequence, OptionalInt.of((int) sequence + 1), PositionMoveRotation.withoutRotation(position, Motion.newEmpty()), EnumSet.noneOf(Relative.class), null, true);
      teleport.allow();
      movement.expectServerTeleport(teleport.id());
      movement.pendingTeleports.get().add(teleport);
    }

    void requestCorrection() {
      controller.movementCorrection(user, PositionMoveRotation.withoutRotation(new Position(200, 90, 200), Motion.newEmpty()));
    }

    void correct() {
      requestCorrection();
      assertTrue(movement.inRecovery);
      scheduled.removeFirst().run();
    }

    void confirm() {
      Teleport teleport = movement.pendingTeleports.get().getFirst();
      controller.beforeTeleportTransactionReceive(user, teleport);
      controller.receiveTeleportAccept(user, teleport.id().getAsInt(), new PositionAndRotation(DISMOUNT, Rotation.zero()));
      assertTrue(movement.pendingTeleports.get().isEmpty());
      assertFalse(movement.inRecovery);
      assertEquals(DISMOUNT, movement.verifiedLastPosition());
    }
  }
}

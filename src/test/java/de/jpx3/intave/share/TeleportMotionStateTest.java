package de.jpx3.intave.share;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.PostTickSimulation;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.OptionalInt;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_21_11;
import static org.junit.jupiter.api.Assertions.*;

class TeleportMotionStateTest {
  @Test
  void upwardTeleportReplacesThePreviousTicksMotionCandidates() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
    User user = UserFactory.createFallback();
    user.meta().protocol().setProtocolVersion(VER_1_21_11);
    MockSimulationEnvironment environment = new MockSimulationEnvironment(user);
    Position before = new Position(4, 84, 6);
    environment.setPosition(before);
    environment.setPostTickMotionCandidates(Collections.singletonList(
      new PostTickSimulation(new Motion(0, -0.0784000015258789, 0), false)
    ));
    Teleport teleport = new Teleport(1, OptionalInt.of(-7),
      PositionMoveRotation.noMotionRelativePosition(new Position(0, 10, 0)), Relative.RELATIVE_POSITION);

    teleport.expectedPositionMoveRotation(before, Rotation.zero()).applyTo(environment);

    assertEquals(94, environment.position().getY(), 0);
    assertEquals(94, environment.verifiedLastPosition().getY(), 0);
    assertTrue(environment.mutableBaseMotionCopy().isZero());
    // ClientPacketListener.handleMovePlayer sets delta movement to zero for XYZ-only flags.
    // A previous falling candidate must not override it in PreviousPostTickBrancher.
    assertTrue(environment.postTickMotionCandidates().isEmpty());
  }
}

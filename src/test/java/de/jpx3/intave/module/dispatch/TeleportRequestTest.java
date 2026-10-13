package de.jpx3.intave.module.dispatch;

import de.jpx3.intave.adapter.MinecraftVersion;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import de.jpx3.intave.share.Rotation;
import de.jpx3.intave.share.Teleport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class TeleportRequestTest {
  @BeforeAll
  static void setup() {
    MinecraftVersion.setCurrent(MinecraftVersions.VER1_21_4);
  }

  @Test
  void legacyAbsoluteRequestKeepsMotionForTrackingAndSending() {
    Teleport teleport = request(false, EnumSet.of(Relative.X, Relative.Y, Relative.Z));
    assertEquals(Boolean.FALSE, teleport.additiveMotionPacket());
    assertEquals(987 / 8000.0D, teleport.change().motion().motionY());
    assertFalse(teleport.relativeSet().contains(Relative.DELTA_Y));
    assertEquals(OptionalInt.of(-12), teleport.id());
    assertEquals(7, teleport.uniqueId());
    assertEquals(Boolean.FALSE, teleport.simulatedOnGround());
  }

  @Test
  void legacyAdditiveRequestRetainsExplosionAndPostTeleportMotionSemantics() {
    Teleport teleport = request(false, EnumSet.of(Relative.Y, Relative.DELTA_Y));
    assertEquals(Boolean.TRUE, teleport.additiveMotionPacket());
    assertEquals((double) (float) 0.123456789, teleport.change().motion().motionY());
    assertTrue(teleport.relativeSet().contains(Relative.DELTA_Y));
  }

  @Test
  void nativeRequestNeedsNoCompanionAndPreservesDoublePrecision() {
    Teleport teleport = request(true, EnumSet.of(Relative.DELTA_Y));
    assertNull(teleport.additiveMotionPacket());
    assertEquals(0.123456789, teleport.change().motion().motionY());
    assertTrue(teleport.relativeSet().contains(Relative.DELTA_Y));
  }

  @Test
  void requestSnapshotsMutableInput() {
    PositionMoveRotation change = new PositionMoveRotation(new Position(1, 2, 3),
      new Motion(0, 0.5, 0), Rotation.zero());
    EnumSet<Relative> flags = EnumSet.of(Relative.Y);
    Teleport teleport = Teleport.of(1, OptionalInt.empty(), change, flags, true, true);
    change.position().setY(100);
    change.motion().setMotionY(100);
    change.rotation().setYaw(100);
    flags.clear();
    assertEquals(2, teleport.change().position().getY());
    assertEquals(0.5, teleport.change().motion().motionY());
    assertEquals(0, teleport.change().rotation().yaw());
    assertTrue(teleport.relativeSet().contains(Relative.Y));
    assertEquals(Boolean.TRUE, teleport.simulatedOnGround());
  }

  private Teleport request(boolean nativeMotion, EnumSet<Relative> flags) {
    return Teleport.of(7, OptionalInt.of(-12),
      new PositionMoveRotation(new Position(0, 10, 0), new Motion(0, 0.123456789, 0), Rotation.zero()),
      flags, false, nativeMotion);
  }
}

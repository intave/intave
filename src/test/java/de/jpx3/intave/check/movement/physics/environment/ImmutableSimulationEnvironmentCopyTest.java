/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.check.movement.physics.environment;

import de.jpx3.intave.check.movement.physics.simulator.BoatSimulator.Status;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Position;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ImmutableSimulationEnvironmentCopyTest {
  @Test
  void boatStateIsFrozenAndCopiedToTarget() {
    MockSimulationEnvironment source = new MockSimulationEnvironment();
    source.setPreviousBoatStatus(Status.IN_AIR);
    source.setBoatStatus(Status.IN_WATER);
    source.setBoatGlide(0.6F);
    source.setBoatWaterLevel(12.5);

    SimulationEnvironment copy = source.immutableCopy();
    source.setBoatStatus(Status.ON_LAND);
    source.setBoatGlide(0.0F);

    assertEquals(Status.IN_AIR, copy.previousBoatStatus());
    assertEquals(Status.IN_WATER, copy.boatStatus());
    assertEquals(0.6F, copy.boatGlide(), 0.0F);
    assertEquals(12.5, copy.boatWaterLevel(), 0.0);
    assertThrows(UnsupportedOperationException.class, () -> copy.setBoatStatus(Status.IN_AIR));

    MockSimulationEnvironment target = new MockSimulationEnvironment();
    copy.commitTo(target);

    assertEquals(Status.IN_AIR, target.previousBoatStatus());
    assertEquals(Status.IN_WATER, target.boatStatus());
    assertEquals(0.6F, target.boatGlide(), 0.0F);
    assertEquals(12.5, target.boatWaterLevel(), 0.0);
  }

  @Test
  void copyDoesNotFollowSourceChanges() {
    MockSimulationEnvironment source = new MockSimulationEnvironment();
    source.setPositionX(1.0);
    source.setPositionY(2.0);
    source.setPositionZ(3.0);
    source.setBaseMotion(0.1, 0.2, 0.3);
    source.setRotation(45.0F, 20.0F);

    SimulationEnvironment copy = source.immutableCopy();

    source.setPositionX(7.0);
    source.setBaseMotion(1.0, 2.0, 3.0);
    source.setRotation(90.0F, 40.0F);

    assertEquals(1.0, copy.positionX(), 0.0);
    assertEquals(0.2, copy.baseMotionY(), 0.0);
    assertEquals(45.0F, copy.rotationYaw(), 0.0F);
    assertEquals(20.0F, copy.rotationPitch(), 0.0F);
  }

  @Test
  void copyRejectsMutations() {
    SimulationEnvironment copy = new MockSimulationEnvironment().immutableCopy();

    assertThrows(UnsupportedOperationException.class, () -> copy.setBaseMotion(1.0, 2.0, 3.0));
    assertThrows(UnsupportedOperationException.class, () -> copy.activeTick(MoveMetric.ALIVE));
    assertThrows(UnsupportedOperationException.class, () -> copy.updateMovement(1.0, 2.0, 3.0, 0.0F, 0.0F, true, false));
  }

  @Test
  void mutableObjectsAreReturnedDefensively() {
    MockSimulationEnvironment source = new MockSimulationEnvironment();
    source.setRotation(45.0F, 20.0F);
    BoundingBox box = BoundingBox.fromBounds(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
    source.setBoundingBox(box);

    SimulationEnvironment copy = source.immutableCopy();

    Vector lookVector = copy.lookVector();
    lookVector.setX(123.0);
    BoundingBox returnedBox = copy.boundingBox();
    returnedBox.makeOriginBox();

    assertNotEquals(123.0, copy.lookVector().getX(), 0.0);
    assertNotSame(box, copy.boundingBox());
    assertFalse(copy.boundingBox().isOriginBox());
  }

  @Test
  void copiedBoundingBoxPreservesOriginFlag() {
    MockSimulationEnvironment source = new MockSimulationEnvironment();
    BoundingBox box = BoundingBox.fromBounds(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
    box.makeOriginBox();
    source.setBoundingBox(box);

    SimulationEnvironment copy = source.immutableCopy();

    assertTrue(copy.boundingBox().isOriginBox());
  }

  @Test
  void copiedMetricDerivedFlyingPacketStateIsFrozen() {
    SimulationEnvironment source = new MockSimulationEnvironment().mutableView();
    source.activeTick(MoveMetric.FLYING_PACKET_ACCURATE);

    SimulationEnvironment copy = source.immutableCopy();

    source.inactiveTick(MoveMetric.FLYING_PACKET_ACCURATE);
    source.inactiveTick(MoveMetric.FLYING_PACKET_ACCURATE);

    assertTrue(copy.receivedFlyingPacketIn(0));
    assertEquals(1, copy.ticks(MoveMetric.FLYING_PACKET_ACCURATE));
    assertEquals(0, copy.ticksPast(MoveMetric.FLYING_PACKET_ACCURATE));
  }

  @Test
  void immutableCopyOfImmutableCopyReturnsSameInstance() {
    SimulationEnvironment copy = new MockSimulationEnvironment().immutableCopy();

    assertSame(copy, copy.immutableCopy());
    assertSame(copy, copy.immutableView());
  }

  @Test
  void commitToCopiesFrozenSnapshotToTarget() {
    MockSimulationEnvironment source = new MockSimulationEnvironment();
    source.setPositionX(1.0);
    source.setPositionY(2.0);
    source.setPositionZ(3.0);
    source.setVerifiedLastPosition(new Position(0.5, 1.5, 2.5), "source");
    source.setLastPosition(0.0, 1.0, 2.0);
    source.setRotation(45.0F, 20.0F);
    source.setLastRotation(30.0F, 10.0F);
    source.setMotionX(0.5);
    source.setMotionY(0.5);
    source.setMotionZ(0.5);
    source.setBaseMotion(0.1, 0.2, 0.3);
    source.setMotionMultiplier(new Vector(0.8, 0.75, 0.8));
    source.setBoundingBox(BoundingBox.fromBounds(1.0, 2.0, 3.0, 4.0, 5.0, 6.0));
    source.setInWater(true);
    source.setInLava(true);
    source.setInWeb(true);
    source.setOnGround(true);
    source.addFallDistance(4.0);
    source.activeTick(MoveMetric.ALIVE);

    SimulationEnvironment copy = source.immutableCopy();

    source.setPositionX(9.0);
    source.setBaseMotion(9.0, 9.0, 9.0);
    source.inactiveTick(MoveMetric.ALIVE);

    MockSimulationEnvironment target = new MockSimulationEnvironment();
    copy.commitTo(target);

    assertEquals(1.0, target.positionX(), 0.0);
    assertEquals(2.0, target.positionY(), 0.0);
    assertEquals(3.0, target.positionZ(), 0.0);
    assertEquals(0.5, target.verifiedLastPositionX(), 0.0);
    assertEquals(0.0, target.lastPositionX(), 0.0);
    assertEquals(45.0F, target.rotationYaw(), 0.0F);
    assertEquals(30.0F, target.lastRotationYaw(), 0.0F);
    assertEquals(0.2, target.baseMotionY(), 0.0);
    assertEquals(new Vector(0.8, 0.75, 0.8), target.motionMultiplier());
    assertTrue(target.inWater());
    assertEquals(4.0, target.fallDistance(), 0.0);
    assertEquals(1, target.ticks(MoveMetric.ALIVE));
    assertEquals(0, target.ticksPast(MoveMetric.ALIVE));
  }
}

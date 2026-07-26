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

final class MutableSimulationEnvironmentViewTest {
  @Test
  void boatStateIsIsolatedAndCommitted() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    SimulationEnvironment view = delegate.mutableView();

    view.setPreviousBoatStatus(Status.IN_AIR);
    view.setBoatStatus(Status.IN_WATER);
    view.setBoatGlide(0.6F);
    view.setBoatWaterLevel(12.5);

    assertEquals(Status.ON_LAND, delegate.boatStatus());
    assertEquals(Status.IN_WATER, view.boatStatus());
    assertEquals(0.6F, view.boatGlide(), 0.0F);
    assertEquals(12.5, view.boatWaterLevel(), 0.0);

    view.commitTo(delegate);

    assertEquals(Status.IN_AIR, delegate.previousBoatStatus());
    assertEquals(Status.IN_WATER, delegate.boatStatus());
    assertEquals(0.6F, delegate.boatGlide(), 0.0F);
    assertEquals(12.5, delegate.boatWaterLevel(), 0.0);
  }

  @Test
  void readThroughFollowsDelegateUntilValueIsOverridden() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setPositionX(1.0);
    delegate.setPositionY(2.0);
    delegate.setPositionZ(3.0);

    SimulationEnvironment view = delegate.mutableView();
    assertEquals(1.0, view.positionX(), 0.0);

    delegate.setPositionX(4.0);
    assertEquals(4.0, view.positionX(), 0.0);

    view.setBaseMotion(7.0, 8.0, 9.0);
    delegate.setBaseMotion(10.0, 11.0, 12.0);

    assertEquals(7.0, view.baseMotionX(), 0.0);
    assertEquals(10.0, delegate.baseMotionX(), 0.0);
    assertEquals(4.0, view.positionX(), 0.0);
  }

  @Test
  void motionMultiplierIsIsolatedAndCommitted() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setMotionMultiplier(new Vector(1.0, 1.0, 1.0));
    SimulationEnvironment view = delegate.mutableView();
    Vector multiplier = new Vector(0.8, 0.75, 0.8);

    view.setMotionMultiplier(multiplier);
    multiplier.setX(2.0);

    assertEquals(new Vector(1.0, 1.0, 1.0), delegate.motionMultiplier());
    assertEquals(new Vector(0.8, 0.75, 0.8), view.motionMultiplier());

    view.commitTo(delegate);

    assertEquals(new Vector(0.8, 0.75, 0.8), delegate.motionMultiplier());
  }

  @Test
  void updateMovementChangesViewWithoutChangingDelegate() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setPositionX(1.0);
    delegate.setPositionY(2.0);
    delegate.setPositionZ(3.0);
    delegate.copyPositionToVerifiedPosition();

    SimulationEnvironment view = delegate.mutableView();
    view.updateMovement(4.0, 6.0, 8.0, 90.0F, 45.0F, true, true);

    assertEquals(1.0, delegate.positionX(), 0.0);
    assertEquals(4.0, view.positionX(), 0.0);
    assertEquals(1.0, view.lastPositionX(), 0.0);
    assertEquals(3.0, view.offsetMotionX(), 0.0);
    assertEquals(90.0F, view.rotationYaw(), 0.0F);
  }

  @Test
  void updateMovementPreservesPreviousRotationAsLastRotation() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setRotation(10.0F, 20.0F);

    SimulationEnvironment view = delegate.mutableView();
    view.updateMovement(0.0, 0.0, 0.0, 90.0F, 45.0F, false, true);

    assertEquals(90.0F, view.rotationYaw(), 0.0F);
    assertEquals(45.0F, view.rotationPitch(), 0.0F);
    assertEquals(10.0F, view.lastRotationYaw(), 0.0F);
    assertEquals(20.0F, view.lastRotationPitch(), 0.0F);
  }

  @Test
  void directRotationOverrideDoesNotRewriteLastRotation() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setRotation(10.0F, 20.0F);

    SimulationEnvironment view = delegate.mutableView();
    view.setRotation(90.0F, 45.0F);

    assertEquals(90.0F, view.rotationYaw(), 0.0F);
    assertEquals(45.0F, view.rotationPitch(), 0.0F);
    assertEquals(10.0F, view.lastRotationYaw(), 0.0F);
    assertEquals(20.0F, view.lastRotationPitch(), 0.0F);
  }

  @Test
  void commitToAnotherEnvironment() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setPositionX(1.0);
    delegate.setPositionY(2.0);
    delegate.setPositionZ(3.0);
    delegate.copyPositionToVerifiedPosition();

    SimulationEnvironment view = delegate.mutableView();
    BoundingBox box = BoundingBox.fromBounds(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
    view.updateMovement(4.0, 6.0, 8.0, 90.0F, 45.0F, true, true);
    view.setVerifiedLastPosition(new Position(9.0, 10.0, 11.0), "test");
    view.setBoundingBox(box);
    view.setBaseMotion(0.1, 0.2, 0.3);
    view.setInWater(true);
    view.setPushedByEntity(true);

    MockSimulationEnvironment target = new MockSimulationEnvironment();
    view.commitTo(target);

    assertEquals(4.0, target.positionX(), 0.0);
    assertEquals(6.0, target.positionY(), 0.0);
    assertEquals(8.0, target.positionZ(), 0.0);
    assertEquals(1.0, target.lastPositionX(), 0.0);
    assertEquals(9.0, target.verifiedLastPositionX(), 0.0);
    assertEquals(0.1, target.baseMotionX(), 0.0);
    assertEquals(box, target.boundingBox());
    assertEquals(90.0F, target.rotationYaw(), 0.0F);
    assertEquals(45.0F, target.rotationPitch(), 0.0F);
    assertFalse(delegate.inWater());
  }

  @Test
  void nestedMutableViewsReadThroughAndCommitThroughEachLayer() {
    MockSimulationEnvironment delegate = new MockSimulationEnvironment();
    delegate.setPositionX(1.0);
    delegate.setPositionY(2.0);
    delegate.setPositionZ(3.0);
    delegate.copyPositionToVerifiedPosition();

    SimulationEnvironment parent = delegate.mutableView();
    BoundingBox parentBox = BoundingBox.fromBounds(1.0, 2.0, 3.0, 2.0, 4.0, 5.0);
    parent.setVerifiedLastPosition(new Position(4.0, 5.0, 6.0), "parent");
    parent.setBoundingBox(parentBox);
    parent.setBaseMotion(0.1, 0.2, 0.3);
    parent.setRotation(10.0F, 20.0F);
    parent.setInWater(true);

    SimulationEnvironment child = parent.mutableView();
    assertEquals(4.0, child.verifiedLastPositionX(), 0.0);
    assertEquals(0.2, child.baseMotionY(), 0.0);
    assertEquals(parentBox, child.boundingBox());
    assertEquals(10.0F, child.rotationYaw(), 0.0F);
    assertTrue(child.inWater());

    BoundingBox childBox = BoundingBox.fromBounds(8.0, 9.0, 10.0, 9.0, 11.0, 12.0);
    child.updateMovement(8.0, 9.0, 10.0, 90.0F, 45.0F, true, true);
    child.setBoundingBox(childBox);
    child.setBaseMotion(0.4, 0.5, 0.6);
    child.setInWater(false);

    assertEquals(1.0, delegate.positionX(), 0.0);
    assertEquals(10.0F, parent.rotationYaw(), 0.0F);
    assertTrue(parent.inWater());
    assertEquals(8.0, child.positionX(), 0.0);
    assertEquals(1.0, child.lastPositionX(), 0.0);
    assertEquals(4.0, child.offsetMotionX(), 0.0);
    assertEquals(10.0F, child.lastRotationYaw(), 0.0F);
    assertEquals(0.5, child.baseMotionY(), 0.0);
    assertFalse(child.inWater());

    child.commitTo(parent);

    assertEquals(1.0, delegate.positionX(), 0.0);
    assertFalse(delegate.inWater());
    assertEquals(8.0, parent.positionX(), 0.0);
    assertEquals(1.0, parent.lastPositionX(), 0.0);
    assertEquals(4.0, parent.offsetMotionX(), 0.0);
    assertEquals(90.0F, parent.rotationYaw(), 0.0F);
    assertEquals(10.0F, parent.lastRotationYaw(), 0.0F);
    assertEquals(0.5, parent.baseMotionY(), 0.0);
    assertEquals(childBox, parent.boundingBox());
    assertFalse(parent.inWater());

    MockSimulationEnvironment target = new MockSimulationEnvironment();
    parent.commitTo(target);

    assertEquals(8.0, target.positionX(), 0.0);
    assertEquals(1.0, target.lastPositionX(), 0.0);
    assertEquals(4.0, target.verifiedLastPositionX(), 0.0);
    assertEquals(0.5, target.baseMotionY(), 0.0);
    assertEquals(childBox, target.boundingBox());
    assertEquals(90.0F, target.rotationYaw(), 0.0F);
    assertFalse(target.inWater());
  }

  @Test
  void nestedCommitToRootAppliesDelegateMutationsFirst() {
    MockSimulationEnvironment target = new MockSimulationEnvironment();
    SimulationEnvironment outer = target.mutableView();
    outer.setBaseMotion(1.0, 2.0, 3.0);

    SimulationEnvironment inner = outer.mutableView();
    inner.setVerifiedLastPosition(new Position(4.0, 5.0, 6.0), "nested commit test");

    inner.commitTo(target);

    assertEquals(1.0, target.baseMotionX(), 0.0);
    assertEquals(2.0, target.baseMotionY(), 0.0);
    assertEquals(3.0, target.baseMotionZ(), 0.0);
    assertEquals(4.0, target.verifiedLastPositionX(), 0.0);
    assertEquals(5.0, target.verifiedLastPositionY(), 0.0);
    assertEquals(6.0, target.verifiedLastPositionZ(), 0.0);
  }
}

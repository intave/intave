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

package de.jpx3.intave.check.movement.physics.branch;

import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.check.movement.physics.update.CausalConstraint;
import de.jpx3.intave.check.movement.physics.update.Reduce;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementSearchBranchEnvironmentCacheTest {
  @Test
  void itemUseProfileOverridesTheBranchLocalSpeedMultiplier() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    MovementSearchBranch branch = MovementSearchBranch.blank(null)
      .withHandActive(true, 0.65F);

    SimulationEnvironment modified = branch.modifiedMutableView(environment);

    assertTrue(branch.moveConfig().isHandActive());
    assertEquals(0.65F, modified.itemUseSpeedMultiplier());
    assertEquals(0.65F, modified.immutableView().itemUseSpeedMultiplier());
    assertEquals(0.65F, modified.immutableCopy().itemUseSpeedMultiplier());
    assertEquals(0.2F, environment.itemUseSpeedMultiplier());
  }

  @Test
  void configurationVariantsShareOneModifiedImmutableEnvironment() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    MovementSearchInput input = MovementSearchInput.forTick(
      null, null, environment.immutableView(), false
    );
    MovementSearchBranch rotated = MovementSearchBranch.blank(input)
      .withRotation(Rotation.of(90.0F, 30.0F));
    MovementSearchBranch configurationVariant = rotated.withHandActive(true);

    SimulationEnvironment first = rotated.modifiedImmutableView(input);
    SimulationEnvironment second = configurationVariant.modifiedImmutableView(input);

    assertSame(first, second);
    assertEquals(90.0F, first.rotationYaw());
    assertEquals(30.0F, first.rotationPitch());
    assertEquals(2, first.depth());
  }

  @Test
  void previousPostTickCandidateDefersSwimmingUntilPreTick() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setInWater(true);
    environment.setEyesInWater(true);
    environment.setSwimming(false);

    Motion candidateMotion = Motion.of(0.25, -0.5, 0.75);
    SimulationEnvironment branch = MovementSearchBranch.blank(null)
      .withPreviousPostTickCandidate(candidateMotion, true)
      .modifiedMutableView(environment);

    assertEquals(candidateMotion, branch.mutableBaseMotionCopy());
    assertTrue(branch.lastSprinting());
    assertFalse(branch.isSwimming());
  }

  @Test
  void markedReductionsAreSplitAcrossSimulatedTicks() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setBaseMotion(1.0, 0.0, 1.0);
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, CausalConstraint.openEnded(0, 1))
    );
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, CausalConstraint.openEnded(0, 2))
    );

    MovementSearchInput input = MovementSearchInput.forTick(null, null, environment, false);
    Collection<MovementSearchBranch> branches = new ArrayList<>();
    new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);

    assertEquals(2, branches.size());
    assertTrue(branches.stream().noneMatch(MovementSearchBranch::canFinishExplicitTick));
    assertTrue(branches.stream().anyMatch(branch ->
      branch.modifiedMutableView(environment).activeSequence() == 0
    ));
    assertTrue(branches.stream().anyMatch(branch -> {
      SimulationEnvironment modified = branch.modifiedMutableView(environment);
      return modified.activeSequence() == 1
        && modified.baseMotionX() == 0.6D
        && modified.baseMotionZ() == 0.6D;
    }));
  }

  @Test
  void unmarkedReductionsCanShareOneSimulatedTick() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setBaseMotion(1.0, 0.0, 1.0);
    environment.allTickAmbiguousUpdates().add(
      new Reduce(false, CausalConstraint.openEnded(0, 1))
    );
    environment.allTickAmbiguousUpdates().add(
      new Reduce(false, CausalConstraint.openEnded(0, 2))
    );

    MovementSearchInput input = MovementSearchInput.forTick(null, null, environment, false);
    Collection<MovementSearchBranch> branches = new ArrayList<>();
    new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);

    assertEquals(3, branches.size());
    assertTrue(branches.stream().anyMatch(branch -> {
      SimulationEnvironment modified = branch.modifiedMutableView(environment);
      return branch.canFinishExplicitTick()
        && modified.activeSequence() == 2
        && modified.baseMotionX() == 0.36D
        && modified.baseMotionZ() == 0.36D;
    }));
  }

  @Test
  void postponedReductionMustRunInTheNextSimulatedTick() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setCurrentTick(1);
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, CausalConstraint.openEnded(0, 1))
    );

    MovementSearchInput input = MovementSearchInput.forTick(null, null, environment, false);
    Collection<MovementSearchBranch> branches = new ArrayList<>();
    new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);

    assertEquals(1, branches.size());
    MovementSearchBranch branch = branches.iterator().next();
    assertTrue(branch.canFinishExplicitTick());
    assertEquals(1, branch.modifiedMutableView(environment).activeSequence());
  }

  @Test
  void remainderCanBeDeferredAfterMarkedReductionRan() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setCurrentTick(1);
    environment.setActiveSequence(1);
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, CausalConstraint.openEnded(0, 1))
    );
    environment.allTickAmbiguousUpdates().add(
      new Reduce(false, CausalConstraint.openEnded(0, 2))
    );

    MovementSearchInput input = MovementSearchInput.forTick(null, null, environment, false);
    Collection<MovementSearchBranch> branches = new ArrayList<>();
    new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);

    assertEquals(2, branches.size());
    assertTrue(branches.stream().anyMatch(branch ->
      !branch.canFinishExplicitTick()
        && branch.modifiedMutableView(environment).activeSequence() == 1
    ));
    assertTrue(branches.stream().anyMatch(branch ->
      branch.canFinishExplicitTick()
        && branch.modifiedMutableView(environment).activeSequence() == 2
    ));
  }

  @Test
  void optionalReductionCanBeSkippedForTheExplicitTick() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setBaseMotion(1.0, 0.0, 1.0);
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, false, CausalConstraint.openEnded(0, 1))
    );

    MovementSearchInput input = MovementSearchInput.forTick(null, null, environment, false);
    Collection<MovementSearchBranch> branches = new ArrayList<>();
    new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);

    assertEquals(2, branches.size());
    assertTrue(branches.stream().allMatch(MovementSearchBranch::canFinishExplicitTick));
    assertTrue(branches.stream().anyMatch(branch ->
      branch.modifiedMutableView(environment).activeSequence() == 0
    ));
    assertTrue(branches.stream().anyMatch(branch ->
      branch.modifiedMutableView(environment).activeSequence() == 1
    ));
  }

  @Test
  void optionalReductionCanBeSkippedForLaterMandatoryReduction() {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setCurrentTick(2);
    environment.setBaseMotion(1.0, 0.0, 1.0);
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, false, CausalConstraint.openEnded(0, 1))
    );
    environment.allTickAmbiguousUpdates().add(
      new Reduce(true, CausalConstraint.openEnded(1, 2))
    );

    MovementSearchInput input = MovementSearchInput.forTick(null, null, environment, false);
    Collection<MovementSearchBranch> branches = new ArrayList<>();
    new UpdateBrancher().branch(input, MovementSearchBranch.blank(input), branches);

    assertEquals(1, branches.size());
    MovementSearchBranch branch = branches.iterator().next();
    SimulationEnvironment modified = branch.modifiedMutableView(environment);
    assertTrue(branch.canFinishExplicitTick());
    assertEquals(2, modified.activeSequence());
    assertEquals(0.6D, modified.baseMotionX());
    assertEquals(0.6D, modified.baseMotionZ());
  }

  @Test
  void toStringIncludesCompleteDebuggingState() {
    MovementSearchBranch branch = MovementSearchBranch.blank(null)
      .withKeypress(1, -1)
      .withHandActive(true)
      .withReduceTicks(2)
      .withReduceBefore(true)
      .withJumped(true)
      .withSprintingSetTo(true)
      .withActualMotionOverride(false)
      .withAlternativeBlockInsideCheck(true)
      .withFlying(true)
      .withExplicitTickFinishAllow(false);

    String description = branch.toString();

    assertTrue(description.startsWith(
      "MovementSearchBranch{configuration=(WA) _RED2_SPR_JMP_HA"
    ));
    assertTrue(description.contains("reduceBefore=true"));
    assertTrue(description.contains("overrideEndMotionToActualMotion=false"));
    assertTrue(description.contains("alternateBlockInsideCheck=true"));
    assertTrue(description.contains("environmentModifierApplied=true"));
    assertTrue(description.contains("canFinishExplicitTick=false"));
    assertTrue(description.contains(
      "frequencyKey=0x" + Long.toHexString(branch.frequencyKey())
    ));
  }
}

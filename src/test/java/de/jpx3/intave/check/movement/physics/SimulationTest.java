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

package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.MockSimulationEnvironment;
import de.jpx3.intave.check.movement.physics.simulator.Simulation;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SimulationTest {
  @Test
  void reusableCopyPreservesBlueDetails() {
    Simulation simulation = simulation();

    simulation.appendBlue("1f");

    assertEquals("1f", simulation.reusableCopy().blueDetails());
  }

  @Test
  void reusableCopyPreservesSimulationCount() {
    Simulation simulation = simulation();
    simulation.setSimulationCount(42);

    assertEquals(42, simulation.reusableCopy().simulationCount());
  }

  @Test
  void reusableCopyPreservesSearchDepth() {
    Simulation simulation = simulation();
    simulation.setSearchDepth(2);

    assertEquals(2, simulation.reusableCopy().searchDepth());
  }

  @Test
  void flushClearsBlueDetails() {
    Simulation simulation = simulation();
    simulation.appendBlue("1f");

    simulation.flush(
      MovementConfiguration.blank(),
      new MockSimulationEnvironment(),
      SimulationResult.untouched(Motion.newEmpty())
    );

    assertEquals("", simulation.blueDetails());
  }

  @Test
  void flushClearsSimulationCount() {
    Simulation simulation = simulation();
    simulation.setSimulationCount(42);

    simulation.flush(
      MovementConfiguration.blank(),
      new MockSimulationEnvironment(),
      SimulationResult.untouched(Motion.newEmpty())
    );

    assertEquals(0, simulation.simulationCount());
  }

  @Test
  void flushClearsSearchDepth() {
    Simulation simulation = simulation();
    simulation.setSearchDepth(2);

    simulation.flush(
      MovementConfiguration.blank(),
      new MockSimulationEnvironment(),
      SimulationResult.untouched(Motion.newEmpty())
    );

    assertEquals(0, simulation.searchDepth());
  }

  @Test
  void finiteSimulationWinsOverNonFiniteDistance() {
    Simulation finite = simulationWith(Motion.newEmpty());
    Simulation nonFinite = simulationWith(new Motion(Double.NaN, 0.0D, 0.0D));

    assertTrue(Double.isFinite(nonFinite.select(finite).offsetDifference()));
    assertTrue(Double.isFinite(finite.select(nonFinite).offsetDifference()));
  }

  @Test
  void packetConfirmedItemUseWinsAnEqualSlotSwitchPrediction() {
    Simulation active = itemUseSimulation(true, false, Motion.newEmpty());
    Simulation unconfirmedSwitch = itemUseSimulation(
      false, true, Motion.newEmpty()
    );

    assertTrue(active.select(unconfirmedSwitch).configuration().isHandActive());
    assertTrue(unconfirmedSwitch.select(active).configuration().isHandActive());
  }

  @Test
  void unconfirmedSlotSwitchWinsWhenItExplainsMovementBetter() {
    Simulation active = itemUseSimulation(
      true, false, new Motion((double) (0.98F * 0.02F), 0.0D, 0.0D)
    );
    Simulation unconfirmedSwitch = itemUseSimulation(
      false, true, Motion.newEmpty()
    );

    Simulation selected = active.select(unconfirmedSwitch);

    assertFalse(selected.configuration().isHandActive());
    assertTrue(selected.environment().legacyItemUseSlotSwitchPending());
  }

  private Simulation simulation() {
    return simulationWith(Motion.newEmpty());
  }

  private Simulation simulationWith(Motion motion) {
    return Simulation.of(
      userWithoutPlayer(),
      MovementConfiguration.blank(),
      new MockSimulationEnvironment(),
      SimulationResult.untouched(motion)
    );
  }

  private Simulation itemUseSimulation(
    boolean handActive, boolean slotSwitchPending, Motion predictedMotion
  ) {
    MockSimulationEnvironment environment = new MockSimulationEnvironment();
    environment.setLegacyItemUseSlotSwitchPending(slotSwitchPending);
    return Simulation.of(
      userWithoutPlayer(),
      MovementConfiguration.blank().withHandActive(handActive),
      environment,
      SimulationResult.untouched(predictedMotion)
    );
  }

  private User userWithoutPlayer() {
    return (User) Proxy.newProxyInstance(
      User.class.getClassLoader(),
      new Class<?>[]{User.class},
      (proxy, method, args) -> {
        if ("hasPlayer".equals(method.getName())) {
          return false;
        }
        return null;
      }
    );
  }
}

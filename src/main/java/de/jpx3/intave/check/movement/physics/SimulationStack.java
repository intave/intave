package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserLocal;

final class SimulationStack {
  private static final UserLocal<SimulationStack> stackUserLocal = UserLocal.withInitial(SimulationStack::new);
  private static final int DEFAULT_DISTANCE = Integer.MAX_VALUE;

  private Simulation simulation;
  private double smallestDistance;

  private int trials;

  SimulationStack() {
    this.smallestDistance = DEFAULT_DISTANCE;
  }

  void restore() {
    simulation = Simulation.invalid();
    smallestDistance = DEFAULT_DISTANCE;
  }

  void tryAppendToState(
    Simulation simulation,
    double newDistance
  ) {
    if (newDistance < this.smallestDistance) {
      appendToState(simulation, newDistance);
    }
  }

  private void appendToState(
    Simulation simulation,
    double newDistance
  ) {
    this.simulation = simulation;
    this.smallestDistance = newDistance;
  }

  boolean noMatch() {
    return simulation == null || this.smallestDistance == DEFAULT_DISTANCE;
  }

  Simulation bestSimulation() {
    return simulation;
  }

  int forward() {
    return configuration().forward();
  }

  int strafe() {
    return configuration().strafe();
  }

  boolean jumped() {
    return configuration().isJumping();
  }

  boolean sprinted() {
    return configuration().isSprinting();
  }

  boolean reduced() {
    return configuration().isReducing();
  }

  boolean handActive() {
    return configuration().isHandActive();
  }

  double smallestDistance() {
    return smallestDistance;
  }

  public int trials() {
    return trials;
  }

  public void setTrials(int trials) {
    this.trials = trials;
  }

  MovementConfiguration configuration() {
    return simulation.configuration();
  }

  static SimulationStack of(User user) {
    SimulationStack simulationStack = stackUserLocal.get(user);
    simulationStack.restore();
    return simulationStack;
  }
}

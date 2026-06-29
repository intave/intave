package de.jpx3.intave.check.movement.physics.evaluation;

import de.jpx3.intave.check.movement.physics.Simulation;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.user.User;

abstract class Evaluator {
  public abstract String shortName();

  public abstract void evaluate(UncertaintyParameters parameters, User user, Simulation simulation, SimulationEnvironment movement);
}

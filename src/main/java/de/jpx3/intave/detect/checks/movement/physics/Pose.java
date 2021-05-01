package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.detect.checks.movement.physics.simulators.DefaultPoseSimulator;
import de.jpx3.intave.detect.checks.movement.physics.simulators.ElytraPoseSimulator;
import de.jpx3.intave.detect.checks.movement.physics.simulators.PoseSimulator;
import de.jpx3.intave.detect.checks.movement.physics.simulators.HorsePoseSimulator;

public enum Pose {
  PLAYER(new DefaultPoseSimulator()),
  ELYTRA(new ElytraPoseSimulator()),
  HORSE(new HorsePoseSimulator());

  private final PoseSimulator calculationPart;

  Pose(PoseSimulator calculationPart) {
    this.calculationPart = calculationPart;
  }

  public PoseSimulator simulator() {
    return calculationPart;
  }
}
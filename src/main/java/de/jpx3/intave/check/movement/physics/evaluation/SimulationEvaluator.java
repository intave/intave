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

package de.jpx3.intave.check.movement.physics.evaluation;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.user.User;

import java.util.Set;

public interface SimulationEvaluator {
  double calculateViolationIncrease(
    User user,
    SimulationEnvironment movement,
    double predictedX, double predictedY, double predictedZ,
    double motionX, double motionY, double motionZ,
    boolean onLadder, boolean collidedWithEntity,
    Set<? super EvaluationTag> tags
  );

  default double calculateHorizontalViolationIncrease(
    User user,
    SimulationEnvironment movement,
    double predictedX, double predictedZ,
    boolean onLadder, boolean collidedWithEntity,
    Set<? super EvaluationTag> tags
  ) {
    return calculateHorizontalViolationIncrease(
      user, movement,
      predictedX, predictedZ,
      movement.offsetMotionX(), movement.offsetMotionZ(),
      onLadder, collidedWithEntity, tags
    );
  }

  default double calculateHorizontalViolationIncrease(
    User user,
    SimulationEnvironment movement,
    double predictedX, double predictedZ,
    double motionX, double motionZ,
    boolean onLadder, boolean collidedWithEntity,
    Set<? super EvaluationTag> tags
  ) {
    double motionY = movement.offsetMotionY();
    return calculateViolationIncrease(
      user, movement,
      predictedX, motionY, predictedZ,
      motionX, motionY, motionZ,
      onLadder, collidedWithEntity, tags
    );
  }

  default double calculateVerticalViolationIncrease(
    User user,
    SimulationEnvironment movement,
    double predictedY,
    boolean onLadder,
    boolean collidedWithEntity,
    Set<? super EvaluationTag> tags
  ) {
    double motionX = movement.offsetMotionX();
    double motionY = movement.offsetMotionY();
    double motionZ = movement.offsetMotionZ();
    return calculateViolationIncrease(
      user, movement,
      motionX, predictedY, motionZ,
      motionX, motionY, motionZ,
      onLadder, collidedWithEntity, tags
    );
  }
}

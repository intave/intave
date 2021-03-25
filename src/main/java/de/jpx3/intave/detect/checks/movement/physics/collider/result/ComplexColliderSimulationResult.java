package de.jpx3.intave.detect.checks.movement.physics.collider.result;

import de.jpx3.intave.detect.checks.movement.physics.ProcessorMotionContext;

public final class ComplexColliderSimulationResult {
  private final ProcessorMotionContext context;
  private final boolean onGround, collidedHorizontally, collidedVertically;
  private final boolean resetMotionX, resetMotionZ;
  private final boolean step;

  public ComplexColliderSimulationResult(
    ProcessorMotionContext context, boolean onGround,
    boolean collidedHorizontally, boolean collidedVertically,
    boolean resetMotionX, boolean resetMotionZ, boolean step
  ) {
    if (context == null) {
      throw new IllegalArgumentException("Context cannot be null");
    }
    this.context = context;
    this.onGround = onGround;
    this.collidedHorizontally = collidedHorizontally;
    this.collidedVertically = collidedVertically;
    this.resetMotionX = resetMotionX;
    this.resetMotionZ = resetMotionZ;
    this.step = step;
  }

  public ProcessorMotionContext context() {
    return context;
  }

  public boolean onGround() {
    return onGround;
  }

  public boolean collidedHorizontally() {
    return collidedHorizontally;
  }

  public boolean collidedVertically() {
    return collidedVertically;
  }

  public boolean step() {
    return step;
  }

  public boolean resetMotionX() {
    return resetMotionX;
  }

  public boolean resetMotionZ() {
    return resetMotionZ;
  }
}
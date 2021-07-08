package de.jpx3.intave.world.collider.complex;

import de.jpx3.intave.detect.checks.movement.physics.MotionVector;
import de.jpx3.intave.tools.MathHelper;

public class ComplexColliderSimulationResult {
  private final MotionVector context;
  private final boolean onGround, collidedHorizontally, collidedVertically;
  private final boolean resetMotionX, resetMotionZ;
  private final boolean step;

  public ComplexColliderSimulationResult(
    MotionVector context, boolean onGround,
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

  public double accuracy(MotionVector motionVector) {
    return MathHelper.distanceOf(context, motionVector);
  }

  public MotionVector context() {
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
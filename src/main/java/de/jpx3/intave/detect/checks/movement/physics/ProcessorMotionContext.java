package de.jpx3.intave.detect.checks.movement.physics;

import de.jpx3.intave.user.UserMetaMovementData;

public final class ProcessorMotionContext {
  public double motionX;
  public double motionY;
  public double motionZ;

  public ProcessorMotionContext() {
    this(0.0, 0.0, 0.0);
  }

  public ProcessorMotionContext(double motionX, double motionY, double motionZ) {
    this.motionX = motionX;
    this.motionY = motionY;
    this.motionZ = motionZ;
  }

  public void reset(double x, double y, double z) {
    this.motionX = x;
    this.motionY = y;
    this.motionZ = z;
  }

  public void resetTo(UserMetaMovementData data) {
    reset(data.physicsMotionX, data.physicsMotionY, data.physicsMotionZ);
  }

  public static ProcessorMotionContext from(ProcessorMotionContext context) {
    return new ProcessorMotionContext(context.motionX, context.motionY, context.motionZ);
  }
}

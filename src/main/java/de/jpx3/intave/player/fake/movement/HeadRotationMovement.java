package de.jpx3.intave.player.fake.movement;

import de.jpx3.intave.math.MathHelper;
import de.jpx3.intave.shade.WrappedMathHelper;

import java.util.concurrent.ThreadLocalRandom;

public class HeadRotationMovement {
  private final static double MAX_PITCH_ROTATION = 30;
  public float prevRotationYaw, prevRotationPitch = 0.0f;
  public float rotationYaw = 0.0f, rotationPitch = 0.0f;

  public void updateHeadRotation(
    double motionX,
    double motionZ,
    double distanceMoved,
    float playerYaw
  ) {
    this.prevRotationPitch = this.rotationPitch;
    this.prevRotationYaw = this.rotationYaw;
    float updatedPitch = updatePitch();
    float updatedYaw = distanceMoved != 0 ? motionYaw(motionX, motionZ) : this.rotationYaw;
    this.rotationPitch = updatedPitch;

    if (MathHelper.distanceInDegrees(updatedYaw, this.rotationYaw) < 100) {
      this.rotationYaw = updatedYaw;
    } else {
      this.rotationYaw = playerYaw + ThreadLocalRandom.current().nextInt(-30, 30);
    }
  }

  private float updatePitch() {
    float newPitch = (float) ThreadLocalRandom.current().nextDouble(prevRotationPitch - 15.0, prevRotationPitch + 15.0);
    newPitch = WrappedMathHelper.clamp_float(newPitch, -50, 50);
    if (inRange(newPitch)) {
      prevRotationPitch = newPitch;
    }
    return newPitch;
  }

  private float motionYaw(double motionX, double motionZ) {
    return (float) (Math.atan2(motionZ, motionX) * 180.0 / Math.PI) - 90.0f;
  }

  private boolean inRange(double value) {
    return value >= -30.0 && value <= HeadRotationMovement.MAX_PITCH_ROTATION;
  }
}
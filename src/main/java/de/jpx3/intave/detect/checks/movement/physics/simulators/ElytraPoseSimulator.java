package de.jpx3.intave.detect.checks.movement.physics.simulators;

import de.jpx3.intave.detect.checks.movement.physics.ProcessorMotionContext;
import de.jpx3.intave.detect.checks.movement.physics.collider.Collider;
import de.jpx3.intave.detect.checks.movement.physics.collider.result.ComplexColliderSimulationResult;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.util.Vector;

public final class ElytraPoseSimulator extends DefaultPoseSimulator {
  @Override
  public ComplexColliderSimulationResult performSimulation(
    User user, ProcessorMotionContext context,
    float forward, float strafe,
    boolean attackReduce, boolean jumped, boolean handActive
  ) {
    UserMetaMovementData movementData = user.meta().movementData();
    float rotationPitch = movementData.rotationPitch;
    Vector lookVector = movementData.lookVector;

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    float f = rotationPitch * 0.017453292F;
    double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
    double dist2 = Math.sqrt(context.motionX * context.motionX + context.motionZ * context.motionZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = WrappedMathHelper.cos(f);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
    context.motionY += movementData.gravity * (-1 + pitchCosine * 0.75);

    if (context.motionY < 0.0D && rotationVectorDistance > 0.0D) {
      double d2 = context.motionY * -0.1D * (double) pitchCosine;
      context.motionY += d2;
      context.motionX += lookVector.getX() * d2 / rotationVectorDistance;
      context.motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
    }

    // 1.9
//                if (f < 0.0F) {
//                  double d9 = d8 * (double) (-WrappedMathHelper.sin(f)) * 0.04D;
//                  predictedMotionY += d9 * 3.2D;
//                  predictedMotionX -= elytraMoveVector.getX() * d9 / d6;
//                  predictedMotionZ -= elytraMoveVector.getZ() * d9 / d6;
//                }
    // 1.16
    if (f < 0.0F && rotationVectorDistance > 0.0D) {
      double d9 = dist2 * (double) (-WrappedMathHelper.sin(f)) * 0.04D;
      context.motionY += d9 * 3.2D;
      context.motionX += -lookVector.getX() * d9 / rotationVectorDistance;
      context.motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
//                  vector3d = vector3d.add(-vector3d1.x * d9 / d1, d9 * 3.2D, -vector3d1.z * d9 / d1);
    }

    // 1.9
    if (rotationVectorDistance > 0.0D) {
      context.motionX += (lookVector.getX() / rotationVectorDistance * dist2 - context.motionX) * 0.1D;
      context.motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - context.motionZ) * 0.1D;
    }
    // 1.16
//                if (d6 > 0.0D) {
//                  predictedMotionX += (elytraMoveVector.getX() / d6 * d1 - predictedMotionX) * 0.1D;
//                  predictedMotionZ += (elytraMoveVector.getZ() / d6 * d1 - predictedMotionZ) * 0.1D;
//                }

    context.motionX *= 0.99f;
    context.motionY *= 0.98f;
    context.motionZ *= 0.99f;

    ComplexColliderSimulationResult collisionResult = Collider.simulateComplexCollision(
      user, context, movementData.inWeb,
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    return collisionResult;
  }

  @Override
  public void prepareNextTick(User user, double positionX, double positionY, double positionZ, double motionX, double motionY, double motionZ) {
    super.prepareNextTick(user, positionX, positionY, positionZ, motionX, motionY, motionZ);
  }

  @Override
  public boolean requiresKeyCalculation() {
    return false;
  }
}
package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.player.Collider;
import de.jpx3.intave.player.collider.complex.ComplexColliderSimulationResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderSimulationResult;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.shade.WrappedMathHelper.cos;
import static de.jpx3.intave.shade.WrappedMathHelper.sin;

public final class ElytraSimulator extends DefaultSimulator {
  @Override
  public ComplexColliderSimulationResult performSimulation(
    User user, MotionVector context,
    float forward, float strafe,
    boolean attackReduce, boolean jumped, boolean handActive
  ) {
    MovementMetadata movementData = user.meta().movement();
    float rotationPitch = movementData.rotationPitch;
    Vector lookVector = movementData.lookVector;

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    float f = rotationPitch * 0.017453292F;
    double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
    double dist2 = Math.sqrt(context.motionX * context.motionX + context.motionZ * context.motionZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = cos(f);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
    context.motionY += movementData.gravity * (-1 + pitchCosine * 0.75);

    if (context.motionY < 0.0D && rotationVectorDistance > 0.0D) {
      double d2 = context.motionY * -0.1D * (double) pitchCosine;
      context.motionY += d2;
      context.motionX += lookVector.getX() * d2 / rotationVectorDistance;
      context.motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
    }

    if (f < 0.0F && rotationVectorDistance > 0.0D) {
      double d9 = dist2 * (double) (-sin(f)) * 0.04D;
      context.motionY += d9 * 3.2D;
      context.motionX += -lookVector.getX() * d9 / rotationVectorDistance;
      context.motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
    }

    if (rotationVectorDistance > 0.0D) {
      context.motionX += (lookVector.getX() / rotationVectorDistance * dist2 - context.motionX) * 0.1D;
      context.motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - context.motionZ) * 0.1D;
    }

    context.motionX *= 0.99f;
    context.motionY *= 0.98f;
    context.motionZ *= 0.99f;

    tryRelinkFlyingPosition(user, context);

    ComplexColliderSimulationResult collisionResult = Collider.simulateComplexCollision(
      user, context, movementData.inWeb,
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    return collisionResult;
  }

  private void tryRelinkFlyingPosition(User user, MotionVector context) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    float rotationPitch = movementData.rotationPitch;
    Vector lookVector = movementData.lookVector;

    double positionX = movementData.verifiedPositionX;
    double positionY = movementData.verifiedPositionY;
    double positionZ = movementData.verifiedPositionZ;

    boolean onGround;
    double resetMotion = movementData.resetMotion();
    double jumpUpwardsMotion = movementData.jumpMotion();

    int interpolations = 0;
    double interpolateX = context.motionX;
    double interpolateY = context.motionY;
    double interpolateZ = context.motionZ;

    for (; interpolations <= 2; interpolations++) {
      SimpleColliderSimulationResult colliderResult = Collider.simulateSimpleCollision(
        player, positionX, positionY, positionZ,
        interpolateX, interpolateY, interpolateZ
      );

      positionX += colliderResult.motionX();
      positionY += colliderResult.motionZ();
      positionZ += colliderResult.motionY();

      double diffX = positionX - movementData.verifiedPositionX;
      double diffY = positionY - movementData.verifiedPositionY;
      double diffZ = positionZ - movementData.verifiedPositionZ;
      onGround = colliderResult.onGround();

      boolean jumpLessThanExpected = colliderResult.motionY() < jumpUpwardsMotion;
      boolean jump = onGround && Math.abs(((colliderResult.motionY()) + jumpUpwardsMotion) - movementData.motionY()) < 1e-5 && jumpLessThanExpected;

      if (!flyingPacket(diffX, diffY, diffZ) && !jump) {
        break;
      }

      float f = rotationPitch * 0.017453292F;
      double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
      double dist2 = Math.sqrt(context.motionX * context.motionX + context.motionZ * context.motionZ);
      double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
      float pitchCosine = cos(f);
      pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
      context.motionY += movementData.gravity * (-1 + pitchCosine * 0.75);

      if (context.motionY < 0.0D && rotationVectorDistance > 0.0D) {
        double d2 = context.motionY * -0.1D * (double) pitchCosine;
        context.motionY += d2;
        context.motionX += lookVector.getX() * d2 / rotationVectorDistance;
        context.motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
      }

      if (f < 0.0F && rotationVectorDistance > 0.0D) {
        double d9 = dist2 * (double) (-sin(f)) * 0.04D;
        context.motionY += d9 * 3.2D;
        context.motionX += -lookVector.getX() * d9 / rotationVectorDistance;
        context.motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
      }

      if (rotationVectorDistance > 0.0D) {
        context.motionX += (lookVector.getX() / rotationVectorDistance * dist2 - context.motionX) * 0.1D;
        context.motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - context.motionZ) * 0.1D;
      }

      context.motionX *= 0.99f;
      context.motionY *= 0.98f;
      context.motionZ *= 0.99f;

      if (Math.abs(interpolateX) < resetMotion) {
        interpolateX = 0;
      }
      if (Math.abs(interpolateY) < resetMotion) {
        interpolateY = 0;
      }
      if (Math.abs(interpolateZ) < resetMotion) {
        interpolateZ = 0;
      }
    }
    if (interpolations != 0) {
      movementData.resetFlyingPacketAccurate();
    }
  }

  @Override
  public String debugName() {
    return "ELYTRA";
  }

  @Override
  public boolean affectedByMovementKeys() {
    return false;
  }
}
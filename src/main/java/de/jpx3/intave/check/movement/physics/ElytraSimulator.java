package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.player.collider.simple.SimpleColliderResult;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import static de.jpx3.intave.check.movement.physics.MoveMetric.FLYING_PACKET_ACCURATE;
import static de.jpx3.intave.share.ClientMath.cos;
import static de.jpx3.intave.share.ClientMath.sin;

final class ElytraSimulator extends BaseSimulator {
  @Override
  public Simulation simulateTick(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    Timings.CHECK_PHYSICS_SIMULATOR_ELYTRA.start();
    float rotationPitch = environment.rotationPitch();
    Vector lookVector = environment.lookVector();

    double positionX = environment.verifiedLastPositionX();
    double positionY = environment.verifiedLastPositionY();
    double positionZ = environment.verifiedLastPositionZ();

    float pitchRad = rotationPitch * 0.017453292F;
    double lookVectorX = lookVector.getX();
    double lookVectorZ = lookVector.getZ();
    double distance = Math.sqrt(lookVectorX * lookVectorX + lookVectorZ * lookVectorZ);
    double dist2 = Math.sqrt(motion.motionX * motion.motionX + motion.motionZ * motion.motionZ);
    double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
    float pitchCosine = cos(pitchRad);
    pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
    motion.motionY += environment.gravity() * (-1 + pitchCosine * 0.75);

    if (motion.motionY < 0.0D && distance > 0.0D) {
      double d2 = motion.motionY * -0.1D * (double) pitchCosine;
      motion.motionY += d2;
      motion.motionX += lookVectorX * d2 / distance;
      motion.motionZ += lookVectorZ * d2 / distance;
    }

    if (pitchRad < 0.0F && distance > 0.0D) {
      double d9 = dist2 * (double) (-sin(pitchRad)) * 0.04D;
      motion.motionY += d9 * 3.2D;
      motion.motionX += -lookVectorX * d9 / distance;
      motion.motionZ += -lookVectorZ * d9 / distance;
    }

    if (distance > 0.0D) {
      motion.motionX += (lookVectorX / distance * dist2 - motion.motionX) * 0.1D;
      motion.motionZ += (lookVectorZ / distance * dist2 - motion.motionZ) * 0.1D;
    }

    motion.motionX *= 0.99f;
    motion.motionY *= 0.98f;
    motion.motionZ *= 0.99f;

    tryRelinkFlyingPosition(user, motion, environment);

    ColliderResult collisionResult = Colliders.collision(
      user, environment, motion, environment.inWeb(),
      positionX, positionY, positionZ
    );
    notePossibleFlyingPacket(user, collisionResult);
    Timings.CHECK_PHYSICS_SIMULATOR_ELYTRA.stop();
    return Simulation.of(user, configuration, collisionResult);
  }

  private void tryRelinkFlyingPosition(User user, Motion motion, SimulationEnvironment environment) {
    Player player = user.player();
    MovementMetadata movementData = user.meta().movement();
    float rotationPitch = environment.rotationPitch();
    Vector lookVector = environment.lookVector();

    double positionX = environment.verifiedLastPositionX();
    double positionY = environment.verifiedLastPositionY();
    double positionZ = environment.verifiedLastPositionZ();

    boolean onGround;
    double resetMotion = environment.resetMotion();
    double jumpUpwardsMotion = environment.jumpMotion();

    int interpolations = 0;
    double interpolateX = motion.motionX;
    double interpolateY = motion.motionY;
    double interpolateZ = motion.motionZ;

    for (; interpolations <= 2; interpolations++) {
      SimpleColliderResult colliderResult = Colliders.simplifiedCollision(
        player, movementData,
        positionX, positionY, positionZ,
        interpolateX, interpolateY, interpolateZ
      );

      positionX += colliderResult.motionX();
      positionY += colliderResult.motionY();
      positionZ += colliderResult.motionZ();

      double diffX = positionX - environment.verifiedLastPositionX();
      double diffY = positionY - environment.verifiedLastPositionY();
      double diffZ = positionZ - environment.verifiedLastPositionZ();
      onGround = colliderResult.onGround();

      boolean jumpLessThanExpected = colliderResult.motionY() < jumpUpwardsMotion;
      boolean jump = onGround && Math.abs(((colliderResult.motionY()) + jumpUpwardsMotion) - environment.motionY()) < 1e-5 && jumpLessThanExpected;

      if (!flyingPacket(user, diffX, diffY, diffZ) && !jump) {
        break;
      }

      float f = rotationPitch * 0.017453292F;
      double rotationVectorDistance = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
      double dist2 = Math.sqrt(motion.motionX * motion.motionX + motion.motionZ * motion.motionZ);
      double rotationVectorLength = Math.sqrt(lookVector.lengthSquared());
      float pitchCosine = cos(f);
      pitchCosine = (float) ((double) pitchCosine * (double) pitchCosine * Math.min(1.0D, rotationVectorLength / 0.4D));
      motion.motionY += environment.gravity() * (-1 + pitchCosine * 0.75);

      if (motion.motionY < 0.0D && rotationVectorDistance > 0.0D) {
        double d2 = motion.motionY * -0.1D * (double) pitchCosine;
        motion.motionY += d2;
        motion.motionX += lookVector.getX() * d2 / rotationVectorDistance;
        motion.motionZ += lookVector.getZ() * d2 / rotationVectorDistance;
      }

      if (f < 0.0F && rotationVectorDistance > 0.0D) {
        double d9 = dist2 * (double) (-sin(f)) * 0.04D;
        motion.motionY += d9 * 3.2D;
        motion.motionX += -lookVector.getX() * d9 / rotationVectorDistance;
        motion.motionZ += -lookVector.getZ() * d9 / rotationVectorDistance;
      }

      if (rotationVectorDistance > 0.0D) {
        motion.motionX += (lookVector.getX() / rotationVectorDistance * dist2 - motion.motionX) * 0.1D;
        motion.motionZ += (lookVector.getZ() / rotationVectorDistance * dist2 - motion.motionZ) * 0.1D;
      }

      motion.motionX *= 0.99f;
      motion.motionY *= 0.98f;
      motion.motionZ *= 0.99f;

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
      movementData.activeTick(FLYING_PACKET_ACCURATE);
    }
  }

  @Override
  public boolean affectedByMovementKeys() {
    return false;
  }
}
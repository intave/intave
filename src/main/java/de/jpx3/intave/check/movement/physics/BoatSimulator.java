package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

import static de.jpx3.intave.share.ClientMath.ceil;
import static de.jpx3.intave.share.ClientMath.floor;

public final class BoatSimulator extends BaseSimulator {
  @Override
  public Simulation simulateTick(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    Timings.CHECK_PHYSICS_SIMULATOR_BOAT.start();
    MovementMetadata movement = user.meta().movement();

    movement.previousBoatStatus = movement.boatStatus;
    movement.boatStatus = boatStatus(user);
    movement.boatGlide = boatGlide(user);
    updateMotion(user, motion);
    controlBoat(user, motion);
    ColliderResult collision = Colliders.collision(
      user, environment, motion, movement.inWeb, movement.verifiedLastPositionX, movement.verifiedLastPositionY, movement.verifiedLastPositionZ
    );

    Timings.CHECK_PHYSICS_SIMULATOR_BOAT.stop();
    return Simulation.of(user, configuration, collision);
  }

  private Status boatStatus(User user) {
    MovementMetadata movement = user.meta().movement();
    Status boatStatus = this.underwaterStatus(user);
    if (boatStatus != null) {
      movement.waterLevel = movement.boundingBox().maxY;
      return boatStatus;
    } else if (this.checkInWater(user)) {
      return Status.IN_WATER;
    } else {
      float glide = this.boatGlide(user);
      if (glide > 0.0F) {
        movement.boatGlide = glide;
        return Status.ON_LAND;
      } else {
        return Status.IN_AIR;
      }
    }
  }

  private boolean checkInWater(User user) {
    MovementMetadata movement = user.meta().movement();
    BoundingBox boundingBox = movement.boundingBox();
    int minX = floor(boundingBox.minX);
    int maxX = ceil(boundingBox.maxX);
    int minY = floor(boundingBox.minY);
    int maxY = ceil(boundingBox.minY + 0.001D);
    int minZ = floor(boundingBox.minZ);
    int maxZ = ceil(boundingBox.maxZ);
    boolean flag = false;
    movement.waterLevel = Double.MIN_VALUE;
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
//          Fluid fluid = Fluids.fluidAt(user, x, y, z);
          Fluid fluid = Fluids.fluidAt(user, x, y, z);
//          if (fluid.isOfWater()) {
          if (fluid.isOfWater()) {
            float f = y + fluid.height();
            movement.waterLevel = Math.max(f, movement.waterLevel);
            flag |= boundingBox.minY < f;
          }
        }
      }
    }
    return flag;
  }

  @Nullable
  private Status underwaterStatus(User user) {
    MovementMetadata movement = user.meta().movement();
    BoundingBox boundingBox = movement.boundingBox();
    double d0 = boundingBox.maxY + 0.001D;
    int minX = floor(boundingBox.minX);
    int maxX = ceil(boundingBox.maxX);
    int minY = floor(boundingBox.maxY);
    int maxY = ceil(d0);
    int minZ = floor(boundingBox.minZ);
    int maxZ = ceil(boundingBox.maxZ);
    boolean flag = false;
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
          Fluid fluid = Fluids.fluidAt(user, x, y, z);
          if (fluid.isOfWater() && d0 < (double) ((float) y + fluid.height())) {
            if (!fluid.isSource()) {
              return Status.UNDER_FLOWING_WATER;
            }
            flag = true;
          }
        }
      }
    }
    return flag ? Status.UNDER_WATER : null;
  }

  private void updateMotion(User user, Motion context) {
    MetadataBundle meta = user.meta();
    MovementMetadata movement = meta.movement();

    //TODO: Missing `hasNoGravity` check: double d1 = this.hasNoGravity() ? 0.0D : (double) -0.04F;
    double d1 = -0.04f;
    double d2 = 0.0D;
    movement.momentum = 0.05F;

    if (movement.previousBoatStatus == Status.IN_AIR && movement.boatStatus != Status.IN_AIR && movement.boatStatus != Status.ON_LAND) {
//      this.waterLevel = this.getPosYHeight(1.0D);
//      this.setPosition(this.getPosX(), (double) (this.getWaterLevelAbove() - this.getHeight()) + 0.101D, this.getPosZ());
      context.motionY = 0;
//      this.lastYd = 0.0D;
      movement.boatStatus = Status.IN_WATER;
    } else {
      switch (movement.boatStatus) {
        case IN_WATER:
          d2 = (movement.waterLevel - movement.verifiedLastPositionY) / (double) movement.height;
          movement.momentum = 0.9f;
          break;
        case UNDER_FLOWING_WATER:
          d1 = -0.0007;
          movement.momentum = 0.9F;
          break;
        case UNDER_WATER:
          d2 = 0.01F;
          movement.momentum = 0.45F;
          break;
        case IN_AIR:
          movement.momentum = 0.9f;
          break;
        case ON_LAND:
          movement.momentum = movement.boatGlide;
          movement.boatGlide /= 2.0F;
          break;
      }

      context.motionX *= movement.momentum;
      context.motionY += d1;
      context.motionZ *= movement.momentum;
      if (d2 > 0.0D) {
        context.motionY = (context.motionY + d2 * 0.06153846016296973D) * 0.75D;
      }
    }
  }

  private void controlBoat(User user, Motion context) {
    MovementMetadata movement = user.meta().movement();
    int forwardInput = movement.clientForwardKey;
    int strafeInput = movement.clientStrafeKey;

    boolean forwardInputDown = forwardInput == 1;
    boolean backInputDown = forwardInput == -1;
    boolean rightInputDown = strafeInput == -1;
    boolean leftInputDown = strafeInput == 1;

    float f = 0.0f;
    if (rightInputDown != leftInputDown && !forwardInputDown && !backInputDown) {
      f += 0.005F;
    }
    if (forwardInputDown) {
      f += 0.04F;
    }
    if (backInputDown) {
      f -= 0.005F;
    }

    float rotationYaw = movement.rotationYaw;
    context.motionX += SinusCache.sin(-rotationYaw * ((float) Math.PI / 180F), false) * f;
    context.motionZ += SinusCache.cos(rotationYaw * ((float) Math.PI / 180F), false) * f;
  }

  private float boatGlide(User user) {
    MovementMetadata movementMeta = user.meta().movement();
    BoundingBox axisalignedbb = BoundingBox.fromPosition(user, movementMeta, movementMeta.position());
    BoundingBox axisalignedbb1 = new BoundingBox(axisalignedbb.minX, axisalignedbb.minY - 0.001D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
    int minX = ClientMath.floor(axisalignedbb1.minX) - 1;
    int maxX = ClientMath.ceil(axisalignedbb1.maxX) + 1;
    int minY = ClientMath.floor(axisalignedbb1.minY) - 1;
    int maxY = ClientMath.ceil(axisalignedbb1.maxY) + 1;
    int minZ = ClientMath.floor(axisalignedbb1.minZ) - 1;
    int maxZ = ClientMath.ceil(axisalignedbb1.maxZ) + 1;
    float slipperiness = 0.0F;
    int collisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int z = minZ; z < maxZ; ++z) {
        int j2 = (x != minX && x != maxX - 1 ? 0 : 1) + (z != minZ && z != maxZ - 1 ? 0 : 1);

        if (j2 != 2) {
          for (int y = minY; y < maxY; ++y) {
            if (j2 <= 0 || y != minY && y != maxY - 1) {
              BoundingBox boundingBox = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
              if (Collision.present(user, movementMeta, boundingBox)) {
                Material material = VolatileBlockAccess.typeAccess(user, x, y, z);
                slipperiness += BlockProperties.of(material).slipperiness();
                ++collisions;
              }
            }
          }
        }
      }
    }

    return slipperiness / (float) collisions;
  }

  public enum Status {
    IN_WATER,
    UNDER_WATER,
    UNDER_FLOWING_WATER,
    ON_LAND,
    IN_AIR
  }

  @Override
  public void simulateAfterTick(
    User user, SimulationEnvironment environment,
    Position position, Motion motion
  ) {
    BoundingBox boundingBox = BoundingBox.fromPosition(user, environment, position);
    environment.setBoundingBox(boundingBox);
  }

  @Override
  public void setback(User user, SimulationEnvironment environment, double predictedX, double predictedY, double predictedZ) {
    Player player = user.player();
    Synchronizer.synchronize(user, player::leaveVehicle);
    environment.dismountRidingEntity("Boat setback");
  }

  @Override
  public float stepHeight() {
    return 0.0f;
  }
}
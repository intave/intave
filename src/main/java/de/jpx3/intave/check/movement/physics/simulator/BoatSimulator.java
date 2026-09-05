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

package de.jpx3.intave.check.movement.physics.simulator;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.check.movement.physics.config.MovementConfiguration;
import de.jpx3.intave.check.movement.physics.environment.SimulationEnvironment;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.SimulationResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMath;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

import static de.jpx3.intave.share.ClientMath.ceil;
import static de.jpx3.intave.share.ClientMath.floor;

public final class BoatSimulator extends BaseSimulator {
  @Override
  public Motion simulatePreTick(
    User user, Motion baseMotion,
    SimulationEnvironment environment
  ) {
    Motion motion = super.simulatePreTick(user, baseMotion, environment);
    Status previousBoatStatus = environment.boatStatus();
    Status boatStatus = boatStatus(user, environment);
    if (previousBoatStatus == Status.IN_AIR && boatStatus != Status.IN_AIR && boatStatus != Status.ON_LAND) {
      boatStatus = Status.IN_WATER;
    }
    environment.setPreviousBoatStatus(previousBoatStatus);
    environment.setBoatStatus(boatStatus);
    environment.setBoatGlide(boatGlide(user, environment));
    return motion;
  }

  @Override
  public Simulation simulateTick(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    Timings.CHECK_PHYSICS_SIMULATOR.start();
    Timings.CHECK_PHYSICS_SIMULATOR_BOAT.start();

    updateMotion(environment, motion);
    controlBoat(environment, configuration, motion);
    SimulationResult collision = Colliders.collision(
      user, environment, motion, environment.inWeb(),
      environment.verifiedLastPositionX(), environment.verifiedLastPositionY(), environment.verifiedLastPositionZ()
    );

    Timings.CHECK_PHYSICS_SIMULATOR_BOAT.stop();
    Timings.CHECK_PHYSICS_SIMULATOR.stop();
    return Simulation.of(user, configuration, environment, collision);
  }

  private Status boatStatus(User user, SimulationEnvironment environment) {
    Status boatStatus = this.underwaterStatus(user, environment);
    if (boatStatus != null) {
      environment.setBoatWaterLevel(environment.boundingBox().maxY);
      return boatStatus;
    } else if (this.checkInWater(user, environment)) {
      return Status.IN_WATER;
    } else {
      float glide = this.boatGlide(user, environment);
      if (glide > 0.0F) {
        environment.setBoatGlide(glide);
        return Status.ON_LAND;
      } else {
        return Status.IN_AIR;
      }
    }
  }

  private boolean checkInWater(User user, SimulationEnvironment environment) {
    BoundingBox boundingBox = environment.boundingBox();
    int minX = floor(boundingBox.minX);
    int maxX = ceil(boundingBox.maxX);
    int minY = floor(boundingBox.minY);
    int maxY = ceil(boundingBox.minY + 0.001D);
    int minZ = floor(boundingBox.minZ);
    int maxZ = ceil(boundingBox.maxZ);
    boolean flag = false;
    double waterLevel = Double.MIN_VALUE;
    for (int x = minX; x < maxX; ++x) {
      for (int y = minY; y < maxY; ++y) {
        for (int z = minZ; z < maxZ; ++z) {
//          Fluid fluid = Fluids.fluidAt(user, x, y, z);
          Fluid fluid = Fluids.fluidAt(user, x, y, z);
//          if (fluid.isOfWater()) {
          if (fluid.isOfWater()) {
            float f = y + fluid.height();
            waterLevel = Math.max(f, waterLevel);
            flag |= boundingBox.minY < f;
          }
        }
      }
    }
    environment.setBoatWaterLevel(waterLevel);
    return flag;
  }

  @Nullable
  private Status underwaterStatus(User user, SimulationEnvironment environment) {
    BoundingBox boundingBox = environment.boundingBox();
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

  private void updateMotion(SimulationEnvironment environment, Motion context) {
    //TODO: Missing `hasNoGravity` check: double d1 = this.hasNoGravity() ? 0.0D : (double) -0.04F;
    double d1 = -0.04f;
    double d2 = 0.0D;
    float momentum = 0.05F;

    Status boatStatus = environment.boatStatus();
    if (environment.previousBoatStatus() == Status.IN_AIR && boatStatus != Status.IN_AIR && boatStatus != Status.ON_LAND) {
//      this.waterLevel = this.getPosYHeight(1.0D);
//      this.setPosition(this.getPosX(), (double) (this.getWaterLevelAbove() - this.getHeight()) + 0.101D, this.getPosZ());
      context.motionY = 0;
//      this.lastYd = 0.0D;
    } else {
      switch (boatStatus) {
        case IN_WATER:
          d2 = (environment.boatWaterLevel() - environment.verifiedLastPositionY()) / environment.height();
          momentum = 0.9f;
          break;
        case UNDER_FLOWING_WATER:
          d1 = -0.0007;
          momentum = 0.9F;
          break;
        case UNDER_WATER:
          d2 = 0.01F;
          momentum = 0.45F;
          break;
        case IN_AIR:
          momentum = 0.9f;
          break;
        case ON_LAND:
          momentum = environment.boatGlide();
          break;
      }

      context.motionX *= momentum;
      context.motionY += d1;
      context.motionZ *= momentum;
      if (d2 > 0.0D) {
        context.motionY = (context.motionY + d2 * 0.06153846016296973D) * 0.75D;
      }
    }
  }

  private void controlBoat(
    SimulationEnvironment environment,
    MovementConfiguration configuration,
    Motion context
  ) {
    int forwardInput = configuration.forward();
    int strafeInput = configuration.strafe();

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

    float rotationYaw = environment.rotationYaw();
    context.motionX += SinusCache.sin(-rotationYaw * ((float) Math.PI / 180F), false) * f;
    context.motionZ += SinusCache.cos(rotationYaw * ((float) Math.PI / 180F), false) * f;
  }

  private float boatGlide(User user, SimulationEnvironment environment) {
    BoundingBox axisalignedbb = BoundingBox.fromPosition(user, environment, environment.position());
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
              if (Collision.present(user, environment, boundingBox)) {
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
  public Motion simulateAfterTick(
    User user, SimulationEnvironment environment,
    MovementConfiguration configuration,
    Position position, Motion motion
  ) {
    BoundingBox boundingBox = BoundingBox.fromPosition(user, environment, position);
    environment.setBoundingBox(boundingBox);
    return motion.copy();
  }

  @Override
  public void setback(User user, SimulationEnvironment environment, double predictedX, double predictedY, double predictedZ) {
    Player player = user.player();
    Synchronizer.synchronize(user, player::leaveVehicle);
    environment.dismountRidingEntity("Boat setback");
  }

  @Override
  public float stepHeight(User user) {
    return 0.0f;
  }
}

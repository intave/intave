package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.collision.Collision;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.executor.Synchronizer;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.player.collider.Colliders;
import de.jpx3.intave.player.collider.complex.ColliderResult;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.ClientMathHelper;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

import static de.jpx3.intave.share.ClientMathHelper.ceil;
import static de.jpx3.intave.share.ClientMathHelper.floor;

public final class BoatSimulator extends Simulator {
  @Override
  public Simulation simulate(
    User user, Motion motion,
    SimulationEnvironment environment,
    MovementConfiguration configuration
  ) {
    MovementMetadata movement = user.meta().movement();

    movement.previousBoatStatus = movement.boatStatus;
    movement.boatStatus = boatStatus(user);
    movement.boatGlide = boatGlide(user);
    updateMotion(user, motion);
    controlBoat(user, motion);
    ColliderResult collision = Colliders.collision(
      user, motion, movement.inWeb, movement.verifiedPositionX, movement.verifiedPositionY, movement.verifiedPositionZ
    );
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
          Fluid fluid = Fluids.fluidAt(user, x, y, z);
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
            if (!fluid.source()) {
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
          d2 = (movement.waterLevel - movement.verifiedPositionY) / (double) movement.height;
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
    Player player = user.player();
    BoundingBox axisalignedbb = BoundingBox.fromPosition(user, user.meta().movement().position());
    BoundingBox axisalignedbb1 = new BoundingBox(axisalignedbb.minX, axisalignedbb.minY - 0.001D, axisalignedbb.minZ, axisalignedbb.maxX, axisalignedbb.minY, axisalignedbb.maxZ);
    int minX = ClientMathHelper.floor(axisalignedbb1.minX) - 1;
    int maxX = ClientMathHelper.ceil(axisalignedbb1.maxX) + 1;
    int minY = ClientMathHelper.floor(axisalignedbb1.minY) - 1;
    int maxY = ClientMathHelper.ceil(axisalignedbb1.maxY) + 1;
    int minZ = ClientMathHelper.floor(axisalignedbb1.minZ) - 1;
    int maxZ = ClientMathHelper.ceil(axisalignedbb1.maxZ) + 1;
    float slipperiness = 0.0F;
    int collisions = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int z = minZ; z < maxZ; ++z) {
        int j2 = (x != minX && x != maxX - 1 ? 0 : 1) + (z != minZ && z != maxZ - 1 ? 0 : 1);

        if (j2 != 2) {
          for (int y = minY; y < maxY; ++y) {
            if (j2 <= 0 || y != minY && y != maxY - 1) {
              BoundingBox boundingBox = new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
              if (Collision.present(player, boundingBox)) {
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
  public void prepareNextTick(User user, double positionX, double positionY, double positionZ, double motionX, double motionY, double motionZ) {
    MovementMetadata movement = user.meta().movement();
    Motion motionVector = movement.motion();
    motionVector.reset(motionX, motionY, motionZ);
    ViolationMetadata violationMetadata = user.meta().violationLevel();

    BoundingBox boundingBox = BoundingBox.fromPosition(user, positionX, positionY, positionZ);
    movement.setBoundingBox(boundingBox);

    if (!violationMetadata.isInActiveTeleportBundle) {
      movement.baseMotionX = motionVector.motionX;
      movement.baseMotionY = motionVector.motionY;
      movement.baseMotionZ = motionVector.motionZ;
    }
  }

  @Override
  public void setback(User user, double predictedX, double predictedY, double predictedZ) {
    Player player = user.player();
    Synchronizer.synchronize(player::leaveVehicle);
    user.meta().movement().dismountRidingEntity("Boat setback");
  }

  @Override
  public String debugName() {
    return "BOAT";
  }

  @Override
  public float stepHeight() {
    return 0.0f;
  }
}
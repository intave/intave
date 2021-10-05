package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.block.fluid.Fluid;
import de.jpx3.intave.block.fluid.Fluids;
import de.jpx3.intave.block.physics.BlockProperties;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.player.collider.Collider;
import de.jpx3.intave.player.collider.complex.ComplexColliderSimulationResult;
import de.jpx3.intave.shade.BoundingBox;
import de.jpx3.intave.shade.Motion;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MetadataBundle;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.user.meta.ViolationMetadata;
import org.bukkit.Material;

import javax.annotation.Nullable;

import static de.jpx3.intave.block.fluid.FluidTag.WATER;
import static de.jpx3.intave.shade.ClientMathHelper.ceil;
import static de.jpx3.intave.shade.ClientMathHelper.floor;

public final class BoatSimulator extends BaseSimulator {
  @Override
  public Simulation performSimulation(
    User user,
    Motion motion,
    float keyForward, float keyStrafe,
    boolean attackReduce, boolean sprinting,
    boolean jumped, boolean handActive
  ) {
    MovementMetadata movement = user.meta().movement();

    movement.previousBoatStatus = movement.boatStatus;
    movement.boatStatus = getBoatStatus(user);
    movement.boatGlide = getBoatGlide(user);
    updateMotion(user, motion);
    controlBoat(user, motion);
    ComplexColliderSimulationResult collision = Collider.simulateComplexCollision(
      user, motion, movement.inWeb, movement.verifiedPositionX, movement.verifiedPositionY, movement.verifiedPositionZ
    );
    return Simulation.of(user, /*temporary solution */MovementConfiguration.select((int)keyForward,(int) keyStrafe, attackReduce, sprinting, jumped, handActive), collision);
  }

  private Status getBoatStatus(User user) {
    MovementMetadata movement = user.meta().movement();
    Status boatStatus = this.getUnderwaterStatus(user);
    if (boatStatus != null) {
      movement.waterLevel = movement.boundingBox().maxY;
      return boatStatus;
    } else if (this.checkInWater(user)) {
      return Status.IN_WATER;
    } else {
      float f = this.getBoatGlide(user);
      if (f > 0.0F) {
        movement.boatGlide = f;
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
          if (fluid.isOf(WATER)) {
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
  private Status getUnderwaterStatus(User user) {
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
          if (fluid.isOf(WATER) && d0 < (double) ((float) y + fluid.height())) {
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

  private float getBoatGlide(User user) {
    BoundingBox boundingBox = user.meta().movement().boundingBox();
    BoundingBox boundingBox1 = new BoundingBox(boundingBox.minX, boundingBox.minY - 0.001D, boundingBox.minZ, boundingBox.maxX, boundingBox.minY, boundingBox.maxZ);
    int minX = floor(boundingBox1.minX) - 1;
    int maxX = ceil(boundingBox1.maxX) + 1;
    int minY = floor(boundingBox1.minY) - 1;
    int maxY = ceil(boundingBox1.maxY) + 1;
    int minZ = floor(boundingBox1.minZ) - 1;
    int maxZ = ceil(boundingBox1.maxZ) + 1;
    float f = 0.0F;
    int k1 = 0;

    for (int x = minX; x < maxX; ++x) {
      for (int z = minZ; z < maxZ; ++z) {
        int coordinatesNotOnLimit = (x != minX && x != maxX - 1 ? 0 : 1) + (z != minZ && z != maxZ - 1 ? 0 : 1);
        if (coordinatesNotOnLimit != 2) {
          for (int y = minY; y < maxY; ++y) {
            if (coordinatesNotOnLimit <= 0 || y != minY && y != maxY - 1) {

              Material material = VolatileBlockAccess.typeAccess(user, x, y, z);
              float slipperiness = BlockProperties.ofType(material).slipperiness();

//              ?
//              BlockState blockstate = this.world.getBlockState(blockpos$mutable);
//              if (!(blockstate.getBlock() instanceof LilyPadBlock) && VoxelShapes.compare(blockstate.getCollisionShape(this.world, blockpos$mutable).withOffset(x, y, z), voxelshape, IBooleanFunction.AND)) {
              f += slipperiness;
              ++k1;
//              }
            }
          }
        }
      }
    }

    return f / (float) k1;
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
    ViolationMetadata violationMetadata = user.meta().violationLevel();

    BoundingBox boundingBox = BoundingBox.fromPosition(user, positionX, positionY, positionZ);
    movement.setBoundingBox(boundingBox);

    if (!violationMetadata.isInActiveTeleportBundle) {
      movement.physicsMotionX = motionVector.motionX;
      movement.physicsMotionY = motionVector.motionY;
      movement.physicsMotionZ = motionVector.motionZ;
    }
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
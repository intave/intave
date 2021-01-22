package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.event.service.entity.WrappedEntity;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.tools.client.PlayerRotationHelper;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Raytracer {
  private static VersionRaytracer versionRaytracer;

  public static void setup() {
    boolean voxelVersion = false;
    try {
      ReflectiveAccess.lookupServerClass("VoxelShape");
      voxelVersion = true;
    } catch (ReflectionFailureException ignored) {}

    String className;
    if(voxelVersion) {
      className = "de.jpx3.intave.world.raytrace.VoxelVersionRaytracer";
    } else {
      className = "de.jpx3.intave.world.raytrace.LegacyVersionRaytracer";
    }
    PatchyLoadingInjector.loadUnloadedClassPatched(Raytracer.class.getClassLoader(), className);
    versionRaytracer = instanceOf(className);
  }

  private static <T> T instanceOf(String className) {
    try {
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static double distanceOf(
    Player player, WrappedEntity entity,
    boolean useAlternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch
  ) {
    return distanceOf(
      player,
      entity.entityBoundingBox(),
      entity.position, entity.alternativePosition,
      useAlternativePositionY,
      prevPosX, prevPosY, prevPosZ,
      prevYaw, pitch
    );
  }

  /**
   * Takes a entity and returns the range between the player and the entity. (Client side its called "getMouseOver" and
   * is from EntityRenderer.java)
   *
   * @return distance the distance between the entity and the eyes of the player 0 means the player is inside of the
   * entity -1 means the player hit outside of the hitbox of the entity >0 means the reach of the player
   */
  public static double distanceOf(
    Player player,
    WrappedAxisAlignedBB entityBoundingBox,
    WrappedEntity.EntityPositionContext position,
    WrappedEntity.EntityPositionContext alternativePosition,
    boolean alternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch
  ) {
    WrappedVector eyeVector = positionEyes(player, prevPosX, prevPosY, prevPosZ);
    double blockReachDistance = 6d;
    WrappedVector interpolatedLookVec = PlayerRotationHelper.wrappedVectorForRotation(pitch, prevYaw);
    WrappedVector lookVector = eyeVector.addVector(
      interpolatedLookVec.xCoord * blockReachDistance,
      interpolatedLookVec.yCoord * blockReachDistance,
      interpolatedLookVec.zCoord * blockReachDistance
    );

    WrappedAxisAlignedBB hitBox = entityBoundingBox.expand(0.1f, 0.1f, 0.1f);
    if (alternativePositionY) {
      hitBox = hitBox.addJustMaxY(alternativePosition.posY - position.posY);
    }
    WrappedMovingObjectPosition movingObjectPosition = hitBox.calculateIntercept(eyeVector, lookVector);
    if (hitBox.isVecInside(eyeVector)) {
      return 0;
    } else if (movingObjectPosition != null) {
      WrappedMovingObjectPosition blockMovingPosition = Raytracer.blockRayTrace(player.getWorld(), player, eyeVector, lookVector);

      double distanceToBlock = blockMovingPosition == null || blockMovingPosition.hitVec == null ? 10 : eyeVector.distanceTo(blockMovingPosition.hitVec);
      double distanceToEntity = eyeVector.distanceTo(movingObjectPosition.hitVec);

      return distanceToBlock < distanceToEntity ? 10 : distanceToEntity;
    }
    return 10;
  }

  private static WrappedVector positionEyes(Player player, double prevPosX, double prevPosY, double prevPosZ) {
    return new WrappedVector(prevPosX, prevPosY + resolvePlayerEyeHeight(player), prevPosZ);
  }

  public static WrappedMovingObjectPosition blockRayTrace(Player player, Location playerLocation) {
    double blockReachDistance = resolveBlockReachDistance(player.getGameMode());
    double eyeHeight = resolvePlayerEyeHeight(player);
    return blockRayTrace(player, playerLocation, playerLocation, blockReachDistance, eyeHeight, 1.0f);
  }

  public static WrappedMovingObjectPosition blockRayTrace(Player player, Location location, Location prevLocation, double blockReachDistance, double eyeHeight, float partialTicks) {
    WrappedVector eyeVector = resolvePositionEyes(location, prevLocation, eyeHeight, partialTicks);
    WrappedVector vec4 = resolveLookVector(location, prevLocation, partialTicks);
    WrappedVector targetVector = eyeVector.addVector(vec4.xCoord * blockReachDistance, vec4.yCoord * blockReachDistance, vec4.zCoord * blockReachDistance);
    return blockRayTrace(location.getWorld(), player, eyeVector, targetVector);
  }

  public static WrappedMovingObjectPosition blockRayTrace(World world, Player player, WrappedVector eyeVector, WrappedVector targetVector) {
    return versionRaytracer.raytrace(world, player, eyeVector, targetVector);
  }

  private static WrappedVector resolvePositionEyes(Location location, Location prevLocation, double eyeHeight, float partialTicks) {
    double posX = location.getX();
    double posY = location.getY();
    double posZ = location.getZ();
    if (partialTicks == 1.0f) {
      return new WrappedVector(posX, posY + eyeHeight, posZ);
    }
    double prevPosX = prevLocation.getX();
    double prevPosY = prevLocation.getY();
    double prevPosZ = prevLocation.getZ();
    double d0 = prevPosX + (posX - prevPosX) * partialTicks;
    double d2 = prevPosY + (posY - prevPosY) * partialTicks + eyeHeight;
    double d3 = prevPosZ + (posZ - prevPosZ) * partialTicks;
    return new WrappedVector(d0, d2, d3);
  }

  private static WrappedVector resolveLookVector(Location location, Location prevLocation, float partialTicks) {
    float rotationYawHead = location.getYaw();
    float rotationPitch = location.getPitch();
    if (partialTicks == 1.0f) {
      return resolveVectorForRotation(rotationPitch, rotationYawHead);
    }
    float prevRotationYawHead = prevLocation.getYaw();
    float prevRotationPitch = prevLocation.getPitch();
    float f = prevRotationPitch + (rotationPitch - prevRotationPitch) * partialTicks;
    float f2 = prevRotationYawHead + (rotationYawHead - prevRotationYawHead) * partialTicks;
    return resolveVectorForRotation(f, f2);
  }

  private static WrappedVector resolveVectorForRotation(float pitch, float yaw) {
    float f = SinusCache.cos(-yaw * 0.017453292f - 3.1415927f, false);
    float f2 = SinusCache.sin(-yaw * 0.017453292f - 3.1415927f, false);
    float f3 = -SinusCache.cos(-pitch * 0.017453292f, false);
    float f4 = SinusCache.sin(-pitch * 0.017453292f, false);
    return new WrappedVector(f2 * f3, f4, f * f3);
  }

  private static double resolvePlayerEyeHeight(Player player) {
    User user = UserRepository.userOf(player);
    UserMetaMovementData movementData = user.meta().movementData();
    float f = 1.62f;
    if (player.isSleeping()) {
      f = 0.2f;
    }
    if (movementData.sneaking) {
      f -= user.meta().clientData().cameraSneakOffset();
    }
    return f;
  }

  private static double resolveBlockReachDistance(GameMode gameMode) {
    return (gameMode == GameMode.CREATIVE) ? 5.0 : 4.5;
  }
}

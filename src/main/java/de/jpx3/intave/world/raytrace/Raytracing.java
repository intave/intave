package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.diagnostics.timings.Timings;
import de.jpx3.intave.event.entity.WrappedEntity;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.client.RotationHelper;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMeta;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Raytracing {
  private static Raytracer raytracer;
  private static final boolean[] BOOLEANSTATES = new boolean[] { false, true };

  public static void setup() {
    String className;
    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      className = "de.jpx3.intave.world.raytrace.v14Raytracer";
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      className = "de.jpx3.intave.world.raytrace.v13Raytracer";
    } else if (MinecraftVersions.VER1_9_0.atOrAbove()) {
      className = "de.jpx3.intave.world.raytrace.v9Raytracer";
    } else {
      className = "de.jpx3.intave.world.raytrace.v8Raytracer";
    }
    PatchyLoadingInjector.loadUnloadedClassPatched(Raytracing.class.getClassLoader(), className);
    raytracer = instanceOf(className);
  }

  private static <T> T instanceOf(String className) {
    try {
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static float reachDistance(Player player) {
    return reachDistance(UserRepository.userOf(player));
  }

  public static float reachDistance(User user) {
    return reachDistance(user.meta());
  }

  public static float reachDistance(UserMeta meta) {
    return meta.abilityData().inGameMode(GameMode.CREATIVE) ? 5.0F : 3.0F;
  }

  /**
   * Calculates the reach with and without mouse delay fix and returns the smallest calculated reach
   * @return
   */
  public static EntityInteractionRaytrace doubleMDFEntityRaytrace(
    Player player, WrappedEntity entity, boolean alternativePositionY,
    double lastPositionX, double lastPositionY, double lastPositionZ,
    float lastRotationYaw,
    float rotationYaw, float rotationPitch,
    double expandHitbox, boolean withoutMouseDelayFix) {
    double blockReachDistance = Raytracing.reachDistance(player);
//    float rotationYaw = movementData.rotationYaw % 360;

    // mouse delay fix
    Raytracing.EntityInteractionRaytrace distanceOfResult = blockConstraintEntityRaytrace(
      player,
      entity, alternativePositionY,
      lastPositionX, lastPositionY, lastPositionZ,
      rotationYaw, rotationPitch,
      expandHitbox
    );
    if (withoutMouseDelayFix && distanceOfResult.reach > blockReachDistance && rotationYaw != lastRotationYaw) {
      // normal
      distanceOfResult = blockConstraintEntityRaytrace(
        player,
        entity, alternativePositionY,
        lastPositionX, lastPositionY, lastPositionZ,
        lastRotationYaw, rotationPitch,
        expandHitbox
      );
    }

    return distanceOfResult;
  }

  /**
   * @param expandBoundingBox should be "0.1f" for a default hitbox
   */
  public static EntityInteractionRaytrace blockConstraintEntityRaytrace(
    Player player, WrappedEntity entity,
    boolean useAlternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double expandBoundingBox
  ) {
    return entityRaytrace(
      player,
      entity.entityBoundingBox(),
      useAlternativePositionY ? (entity.alternativePosition.posY - entity.position.posY) : 0,
      prevPosX, prevPosY, prevPosZ,
      prevYaw, pitch,
      expandBoundingBox,
      EntityRaytraceBlockConstraint.ACCEPT_BLOCKS
    );
  }

  /**
   * @param expandBoundingBox should be "0.1f" for a default hitbox
   */
  public static EntityInteractionRaytrace blockIgnoringEntityRaytrace(
    Player player, WrappedEntity entity,
    boolean useAlternativePositionY,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double expandBoundingBox
  ) {
    return entityRaytrace(
      player,
      entity.entityBoundingBox(),
      useAlternativePositionY ? (entity.alternativePosition.posY - entity.position.posY) : 0,
      prevPosX, prevPosY, prevPosZ,
      prevYaw, pitch,
      expandBoundingBox,
      EntityRaytraceBlockConstraint.IGNORE_BLOCKS
    );
  }

  /**
   * Takes a entity and returns the range between the player and the entity. (Client side its called "getMouseOver" and
   * is from EntityRenderer.java)
   *
   * @return distance the distance between the entity and the eyes of the player 0 means the player is inside of the
   * entity -1 means the player hit outside of the hitbox of the entity >0 means the reach of the player
   */
  public static EntityInteractionRaytrace entityRaytrace(
    Player player,
    WrappedAxisAlignedBB entityBoundingBox,
    double alternativeYDifference,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double boundingBoxExpansion,
    EntityRaytraceBlockConstraint rayTraceBlocks
  ) {
    Timings.SERVICE_RAYTRACER_ENTITY.start();
    WrappedVector eyeVector = positionEyes(player, prevPosX, prevPosY, prevPosZ);
    double blockReachDistance = 6d;
    double attackReachDistance = reachDistance(player);
    double lastReach = 10;
    WrappedVector lastHitVec = null;
    for(boolean fastMath : BOOLEANSTATES) {
      if (lastReach < attackReachDistance)
        break;

      WrappedVector interpolatedLookVec = RotationHelper.wrappedVectorForRotation(pitch, prevYaw, fastMath);
      WrappedVector lookVector = eyeVector.addVector(
        interpolatedLookVec.xCoord * blockReachDistance,
        interpolatedLookVec.yCoord * blockReachDistance,
        interpolatedLookVec.zCoord * blockReachDistance
      );

      WrappedAxisAlignedBB hitBox = entityBoundingBox.expand(boundingBoxExpansion, boundingBoxExpansion, boundingBoxExpansion);
      if (alternativeYDifference != 0) {
        hitBox = hitBox.addJustMaxY(alternativeYDifference);
      }
      WrappedMovingObjectPosition movingObjectPosition = hitBox.calculateIntercept(eyeVector, lookVector);
      if (hitBox.isVecInside(eyeVector)) {
        lastReach = 0;
        lastHitVec = null;
      } else if (movingObjectPosition != null) {
        double distanceToEntity = eyeVector.distanceTo(movingObjectPosition.hitVec);
        double reach;
        if (rayTraceBlocks == EntityRaytraceBlockConstraint.ACCEPT_BLOCKS) {
          WrappedMovingObjectPosition blockMovingPosition = Raytracing.blockRayTrace(player.getWorld(), player, eyeVector, lookVector);
          double distanceToBlock = blockMovingPosition == null || blockMovingPosition.hitVec == null ? 10 : eyeVector.distanceTo(blockMovingPosition.hitVec);
          reach = distanceToBlock < distanceToEntity ? 10 : distanceToEntity;
        } else {
          reach = distanceToEntity;
        }
//        if (fastMath)
//          Bukkit.broadcastMessage("" + (lastReach - reach));
        if (reach < lastReach) {
          lastReach = reach;
          lastHitVec = movingObjectPosition.hitVec;
        }
      }
    }

    Timings.SERVICE_RAYTRACER_ENTITY.stop();
    return new EntityInteractionRaytrace(lastHitVec, lastReach);
  }

  public static class EntityInteractionRaytrace {
    public final WrappedVector hitVec;
    public final double reach;
    public EntityInteractionRaytrace(WrappedVector hitVec, double distance) {
      this.hitVec = hitVec;
      this.reach = distance;
    }
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
    try {
      Timings.SERVICE_RAYTRACER_BLOCK.start();
      return raytracer.raytrace(world, player, eyeVector, targetVector);
    } finally {
      Timings.SERVICE_RAYTRACER_BLOCK.stop();
    }
  }

  public static WrappedVector resolvePositionEyes(Location location, Location prevLocation, double eyeHeight, float partialTicks) {
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
    return user.meta().movementData().eyeHeight();
  }

  private static double resolveBlockReachDistance(GameMode gameMode) {
    return (gameMode == GameMode.CREATIVE) ? 5.0 : 4.5;
  }

  public enum EntityRaytraceBlockConstraint {
    IGNORE_BLOCKS,
    ACCEPT_BLOCKS,
  }
}

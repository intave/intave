package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.diagnostic.timings.Timings;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.math.SinusCache;
import de.jpx3.intave.module.tracker.entity.Entity;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.MovingObjectPosition;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.MetadataBundle;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Raytracing {
  private static Raytracer raytracer;
  private static final boolean[] PESSIMISTIC_BOOLEAN_ORDER = new boolean[]{false, true};

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
//    raytracer = new UniversalRaytracer();
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static float reachDistanceOf(Player player) {
    return reachDistanceOf(UserRepository.userOf(player));
  }

  public static float reachDistanceOf(User user) {
    return reachDistanceOf(user.meta());
  }

  public static float reachDistanceOf(MetadataBundle meta) {
    return meta.abilities().inGameMode(GameMode.CREATIVE) ? 5.0F : 3.0F;
  }

  /**
   * Calculates the reach with and without mouse delay fix and returns the smallest calculated reach
   *
   * @return
   */
  public static Raytrace doubleMDFBlockConstraintEntityRaytrace(
      Player player, Entity entity, boolean alternativePositionY,
      double lastPositionX, double lastPositionY, double lastPositionZ,
      float lastRotationYaw,
      float rotationYaw, float rotationPitch,
      double expandHitbox, boolean withoutMouseDelayFix) {
    double blockReachDistance = Raytracing.reachDistanceOf(player);
//    float rotationYaw = movementData.rotationYaw % 360;

    // mouse delay fix
    Raytrace distanceOfResult = blockConstraintEntityRaytrace(
        player,
        entity, alternativePositionY,
        lastPositionX, lastPositionY, lastPositionZ,
        rotationYaw, rotationPitch,
        expandHitbox
    );
    if (withoutMouseDelayFix && distanceOfResult.reach() > blockReachDistance && rotationYaw != lastRotationYaw) {
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
  public static Raytrace blockConstraintEntityRaytrace(
      Player player, Entity entity,
      boolean useAlternativePositionY,
      double prevPosX, double prevPosY, double prevPosZ,
      float prevYaw, float pitch,
      double expandBoundingBox
  ) {
    return entityRaytrace(
        player,
        entity.boundingBox(),
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
  public static Raytrace blockIgnoringEntityRaytrace(
      Player player, Entity entity,
      boolean useAlternativePositionY,
      double prevPosX, double prevPosY, double prevPosZ,
      float prevYaw, float pitch,
      double expandBoundingBox
  ) {
    return entityRaytrace(
        player,
        entity.boundingBox(),
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
   * entity -1 means the player hit outside of the hitbox of the entity greater than 0 means the reach of the player
   */
  public static Raytrace entityRaytrace(
    Player player,
    BoundingBox entityBoundingBox,
    double alternativeYDifference,
    double prevPosX, double prevPosY, double prevPosZ,
    float prevYaw, float pitch,
    double boundingBoxExpansion,
    EntityRaytraceBlockConstraint rayTraceBlocks
  ) {
    Timings.SERVICE_RAYTRACER_ENTITY.start();
    NativeVector eyeVector = positionEyes(player, prevPosX, prevPosY, prevPosZ);
    double blockReachDistance = 6;
    double attackReachDistance = reachDistanceOf(player);
    double lastReach = 10;
    NativeVector lastHitVec = null;
    for (boolean fastMath : PESSIMISTIC_BOOLEAN_ORDER) {
      if (lastReach < attackReachDistance)
        break;
      NativeVector interpolatedLookVec = wrappedVectorForRotation(pitch, prevYaw, fastMath);
      NativeVector lookVector = eyeVector.addVector(
          interpolatedLookVec.xCoord * blockReachDistance,
          interpolatedLookVec.yCoord * blockReachDistance,
          interpolatedLookVec.zCoord * blockReachDistance
      );
      BoundingBox hitBox = entityBoundingBox.grow(boundingBoxExpansion, boundingBoxExpansion, boundingBoxExpansion);
      if (alternativeYDifference != 0) {
        hitBox = hitBox.addJustMaxY(alternativeYDifference);
      }
      MovingObjectPosition movingObjectPosition = hitBox.calculateIntercept(eyeVector, lookVector);
      if (hitBox.isVecInside(eyeVector)) {
        lastReach = 0;
        lastHitVec = null;
      } else if (movingObjectPosition != null) {
        double distanceToEntity = eyeVector.distanceTo(movingObjectPosition.hitVec);
        double reach;
        boolean blockRaytrace = false;
        if (rayTraceBlocks == EntityRaytraceBlockConstraint.ACCEPT_BLOCKS) {
          MovingObjectPosition blockMovingPosition = Raytracing.blockRayTrace(player.getWorld(), player, eyeVector, lookVector);
          double distanceToBlock = blockMovingPosition == null || blockMovingPosition.hitVec == null ? 10 : eyeVector.distanceTo(blockMovingPosition.hitVec);
          reach = distanceToBlock < distanceToEntity ? 10 : distanceToEntity;
          blockRaytrace = true;
        } else {
          reach = distanceToEntity;
        }
        if (reach < lastReach && (reach < attackReachDistance || blockRaytrace)) {
          lastReach = reach;
          lastHitVec = movingObjectPosition.hitVec;
        }
      }
    }

    Timings.SERVICE_RAYTRACER_ENTITY.stop();
    return Raytrace.ofNative(eyeVector, lastHitVec, lastReach);
  }

  private static NativeVector wrappedVectorForRotation(float pitch, float prevYaw, boolean fastMath) {
    float var3 = SinusCache.cos(-prevYaw * 0.017453292f - (float) Math.PI, fastMath);
    float var4 = SinusCache.sin(-prevYaw * 0.017453292F - (float) Math.PI, fastMath);
    float var5 = -SinusCache.cos(-pitch * 0.017453292f, fastMath);
    float var6 = SinusCache.sin(-pitch * 0.017453292f, fastMath);
    return new NativeVector(var4 * var5, var6, var3 * var5);
  }

  private static NativeVector positionEyes(Player player, double prevPosX, double prevPosY, double prevPosZ) {
    return new NativeVector(prevPosX, prevPosY + resolvePlayerEyeHeight(player), prevPosZ);
  }

  public static MovingObjectPosition blockShrinkRayTrace(Player player, Location playerLocation, double shrik) {
    double blockReachDistance = resolveBlockReachDistance(player.getGameMode());
    double eyeHeight = resolvePlayerEyeHeight(player);
    return blockRayTrace(player, playerLocation, playerLocation, blockReachDistance, eyeHeight, 1.0f);
  }

  public static MovingObjectPosition blockShrinkRayTrace(Player player, Location location, Location prevLocation, double blockReachDistance, double eyeHeight, float partialTicks) {
    NativeVector eyeVector = resolvePositionEyes(location, prevLocation, eyeHeight, partialTicks);
    NativeVector lookVector = resolveLookVector(location, prevLocation, partialTicks);
    NativeVector targetVector = eyeVector.addVector(lookVector.xCoord * blockReachDistance, lookVector.yCoord * blockReachDistance, lookVector.zCoord * blockReachDistance);
    return blockShrinkRayTrace(location.getWorld(), player, eyeVector, targetVector);
  }

  public static MovingObjectPosition blockShrinkRayTrace(World world, Player player, NativeVector eyeVector, NativeVector targetVector) {
    try {
      Timings.SERVICE_RAYTRACER_BLOCK.start();
      return raytracer.raytrace(world, player, eyeVector, targetVector);
    } finally {
      Timings.SERVICE_RAYTRACER_BLOCK.stop();
    }
  }

  public static MovingObjectPosition blockRayTrace(Player player, Location playerLocation) {
    double blockReachDistance = resolveBlockReachDistance(player.getGameMode());
    double eyeHeight = resolvePlayerEyeHeight(player);
    return blockRayTrace(player, playerLocation, playerLocation, blockReachDistance, eyeHeight, 1.0f);
  }

  public static MovingObjectPosition blockRayTrace(Player player, Location location, Location prevLocation, double blockReachDistance, double eyeHeight, float partialTicks) {
    NativeVector eyeVector = resolvePositionEyes(location, prevLocation, eyeHeight, partialTicks);
    NativeVector vec4 = resolveLookVector(location, prevLocation, partialTicks);
    NativeVector targetVector = eyeVector.addVector(vec4.xCoord * blockReachDistance, vec4.yCoord * blockReachDistance, vec4.zCoord * blockReachDistance);
    return blockRayTrace(location.getWorld(), player, eyeVector, targetVector);
  }

  public static MovingObjectPosition blockRayTrace(World world, Player player, NativeVector eyeVector, NativeVector targetVector) {
    try {
      Timings.SERVICE_RAYTRACER_BLOCK.start();
      return raytracer.raytrace(world, player, eyeVector, targetVector);
    } finally {
      Timings.SERVICE_RAYTRACER_BLOCK.stop();
    }
  }

  public static NativeVector resolvePositionEyes(Location location, Location prevLocation, double eyeHeight, float partialTicks) {
    double posX = location.getX();
    double posY = location.getY();
    double posZ = location.getZ();
    if (partialTicks == 1.0f) {
      return new NativeVector(posX, posY + eyeHeight, posZ);
    }
    double prevPosX = prevLocation.getX();
    double prevPosY = prevLocation.getY();
    double prevPosZ = prevLocation.getZ();
    double d0 = prevPosX + (posX - prevPosX) * partialTicks;
    double d2 = prevPosY + (posY - prevPosY) * partialTicks + eyeHeight;
    double d3 = prevPosZ + (posZ - prevPosZ) * partialTicks;
    return new NativeVector(d0, d2, d3);
  }

  private static NativeVector resolveLookVector(Location location, Location prevLocation, float partialTicks) {
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

  private static NativeVector resolveVectorForRotation(float pitch, float yaw) {
    float f = SinusCache.cos(-yaw * 0.017453292f - 3.1415927f, false);
    float f2 = SinusCache.sin(-yaw * 0.017453292f - 3.1415927f, false);
    float f3 = -SinusCache.cos(-pitch * 0.017453292f, false);
    float f4 = SinusCache.sin(-pitch * 0.017453292f, false);
    return new NativeVector(f2 * f3, f4, f * f3);
  }

  private static double resolvePlayerEyeHeight(Player player) {
    User user = UserRepository.userOf(player);
    return user.meta().movement().eyeHeight();
  }

  private static double resolveBlockReachDistance(GameMode gameMode) {
    return (gameMode == GameMode.CREATIVE) ? 5.0 : 4.5;
  }
}

package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.reflect.Reflection;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.tools.client.SinusCache;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.patchy.PatchyLoadingInjector;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class Raytracer {
  private static VersionRaytracer versionRaytracer;

  public static void setup() {
    boolean voxelVersion = false;
    try {
      Reflection.lookupServerClass("VoxelShape");
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

  private static  <T> T instanceOf(String className) {
    try {
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
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
    float f = 1.62f;
    if (player.isSleeping()) {
      f = 0.2f;
    }
    if (player.isSneaking()) {
      f -= UserRepository.userOf(player).meta().clientData().cameraSneakOffset();
    }
    return f;
  }

  private static double resolveBlockReachDistance(GameMode gameMode) {
    return (gameMode == GameMode.CREATIVE) ? 5.0 : 4.5;
  }
}

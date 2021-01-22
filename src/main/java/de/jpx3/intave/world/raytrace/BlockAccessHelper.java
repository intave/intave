package de.jpx3.intave.world.raytrace;

import de.jpx3.intave.access.IntaveException;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.tools.wrapper.WrappedMovingObjectPosition;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static de.jpx3.intave.reflect.ReflectiveAccess.lookupServerClass;

public final class BlockAccessHelper {
  private static boolean MINECRAFT_AUSRUTSCHER = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE) && !ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.AQUATIC_UPDATE);

  private static Method getChunkAtMethod;
  private static Method getTypeMethod;
  private static Constructor<?> blockPositionConstructor;
  private static Method getBlockDataMethod;
  private static Object airBlockData;

  static {
    try {
      getChunkAtMethod = lookupServerClass("World").getMethod("getChunkAt", Integer.TYPE, Integer.TYPE);
      getTypeMethod = lookupServerClass("Chunk").getMethod(MINECRAFT_AUSRUTSCHER ? "getBlockData" : "getType", lookupServerClass("BlockPosition"));
      blockPositionConstructor = lookupServerClass("BlockPosition").getConstructor(Integer.TYPE, Integer.TYPE, Integer.TYPE);
      getBlockDataMethod = lookupServerClass("Chunk").getMethod("getBlockData", lookupServerClass("BlockPosition"));
    } catch (NoSuchMethodException exception) {
      exception.printStackTrace();
    }

    try {
      Object blockAir;
      blockAir = lookupServerClass("Blocks").getField("AIR").get(null);
      Class<?> blockAirClass = blockAir.getClass();
      airBlockData = blockAirClass.getMethod("getBlockData").invoke(blockAir);
    } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException | InvocationTargetException exception) {
      exception.printStackTrace();
    }
  }

  public static Object resolveNativeBlock(Location location) {
    double posX = location.getX();
    double posY = location.getY();
    double posZ = location.getZ();
    int intPosX = floor_double(posX);
    int intPosY = floor_double(posY);
    int intPosZ = floor_double(posZ);

    try {
      Object nativeWorld = resolveNativeWorld(location.getWorld());
      Object chunk = getChunkAtMethod.invoke(nativeWorld,intPosX >> 4, intPosZ >> 4);
      Object blockPosition = generateBlockPosition(intPosX, intPosY, intPosZ);
      return getTypeMethod.invoke(chunk, blockPosition);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException();
    }
  }

  private static int floor_double(double value) {
    int i = (int)value;
    return (value < i) ? (i - 1) : i;
  }

  public static Object generateBlockPosition(Location location) {
    return generateBlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static Object generateBlockPosition(int posX, int posY, int posZ) {
    try {
      return blockPositionConstructor.newInstance(posX, posY, posZ);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IntaveException(e);
    }
  }

  public static Object resolveNativeWorld(World world) {
    return ReflectiveAccess.handleResolver().resolveWorldHandleOf(world);
  }

  public static Object resolveBlockData(Location location) {
    if (!isValid(location)) {
      return airBlockData;
    }

    double posX = location.getX();
    double posY = location.getY();
    double posZ = location.getZ();
    int intPosX = floor_double(posX);
    int intPosY = floor_double(posY);
    int intPosZ = floor_double(posZ);

    try {
      Object nativeWorld = resolveNativeWorld(location.getWorld());
      Object chunk = getChunkAtMethod.invoke(nativeWorld,intPosX >> 4, intPosZ >> 4);
      Object nativeBlockPosition = generateBlockPosition(intPosX, intPosY, intPosZ);
      return getBlockDataMethod.invoke(chunk, nativeBlockPosition);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException();
    }
  }

  private static boolean isValid(Location location) {
    return location.getX() >= -30000000 && location.getZ() >= -30000000 && location.getX() < 30000000 && location.getZ() < 30000000 && location.getY() >= 0 && location.getY() < 256;
  }

  public static boolean liquidCheck(Object nativeBlock, Object blockData, boolean stopOnLiquid) {
    //block1.a(iblockstate1, stopOnLiquid) <bool>
    try {
      Method method = nativeBlock.getClass().getMethod("a", lookupServerClass("IBlockData"), Boolean.TYPE);
      return (boolean) method.invoke(nativeBlock, blockData, stopOnLiquid);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
      throw new IllegalStateException(exception);
    }
  }

  //block1.a(nativeWorld, BlockAccessHelper.generateBlockPosition(byCache), vec31.convertToNativeVec3(), vec32.convertToNativeVec3());
  public static WrappedMovingObjectPosition blockRaytrace(Object nmsBlock, Object nmsWorld, Object blockPosition, Object nativeEyeVector, Object nativeTargetVector) {
    try {
      Method raytraceMethod = nmsBlock.getClass().getMethod(
        "a",
        lookupServerClass("World"),
        lookupServerClass("BlockPosition"),
        lookupServerClass("Vec3D"),
        lookupServerClass("Vec3D")
      );
//      Bukkit.broadcastMessage(String.valueOf(raytraceMethod));
      Object movingObjectPosition = raytraceMethod.invoke(nmsBlock, nmsWorld, blockPosition, nativeEyeVector, nativeTargetVector);
      return movingObjectPosition == null ? null : WrappedMovingObjectPosition.fromNativeMovingObjectPosition(movingObjectPosition);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
      throw new IntaveInternalException(exception);
    }
  }
}
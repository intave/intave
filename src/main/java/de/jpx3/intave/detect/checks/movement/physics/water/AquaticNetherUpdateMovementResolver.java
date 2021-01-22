package de.jpx3.intave.detect.checks.movement.physics.water;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.ReflectiveAccess;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.tools.wrapper.WrappedVector;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserMetaMovementData;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;

public final class AquaticNetherUpdateMovementResolver extends AquaticWaterMovementBase {
  private MethodHandle fluidMethodHandle;
  private MethodHandle fluidTaggedMethodHandle;
  private MethodHandle fluidHeightMethodHandle;
  private MethodHandle fluidFlowMethodHandle;
  private MethodHandle worldTypeMethodHandle;
  private Object fluidTagWater;
  private Class<?> blockPositionClass;

  public AquaticNetherUpdateMovementResolver() {
    try {
      setup();
    } catch (Exception e) {
      e.printStackTrace();
      throw new ReflectionFailureException(e);
    }
  }

  @Override
  public void setup() throws Exception {
    MinecraftVersion minecraftVersion = ProtocolLibAdapter.serverVersion();
    if (minecraftVersion.isAtLeast(ProtocolLibAdapter.AQUATIC_UPDATE)) {
      loadFluidMethodHandle();
      loadFluidTaggedMethodHandle();
      loadFluidTagWater();
      loadFluidHeightMethodHandle();
      loadBlockPositionClass();
      loadFluidFlowMethodHandle();
      loadWorldTypeMethodHandle();
      loadFluidEmptyMethodHandle();
    }
  }

  private void loadFluidMethodHandle() throws NoSuchMethodException, IllegalAccessException {
    Class<?> fluidClass = ReflectiveAccess.lookupServerClass("Fluid");
    MethodType methodType = resolveFluidMethodType(fluidClass);
    fluidMethodHandle = MethodHandles
      .lookup()
      .findVirtual(ReflectiveAccess.NMS_WORLD_SERVER_CLASS, "getFluid", methodType);
  }

  private MethodType resolveFluidMethodType(Class<?> fluidClass) {
    Class<?> blockPositionClass = ReflectiveAccess.lookupServerClass("BlockPosition");
    return MethodType.methodType(fluidClass, blockPositionClass);
  }

  private void loadFluidTaggedMethodHandle() throws NoSuchMethodException, IllegalAccessException {
    Class<?> fluidClass = ReflectiveAccess.lookupServerClass("Fluid");
    MethodType methodType = resolveFluidTaggedMethodType();
    fluidTaggedMethodHandle = MethodHandles
      .lookup()
      .findVirtual(fluidClass, "a", methodType);
  }

  private MethodType resolveFluidTaggedMethodType() {
    Class<?> tagClass = ReflectiveAccess.lookupServerClass("Tag");
    return MethodType.methodType(Boolean.TYPE, tagClass);
  }

  private void loadFluidTagWater() throws NoSuchFieldException, IllegalAccessException {
    fluidTagWater = ReflectiveAccess.lookupServerClass("TagsFluid")
      .getField("WATER")
      .get(null);
  }

  private void loadBlockPositionClass() {
    blockPositionClass = ReflectiveAccess.lookupServerClass("BlockPosition");
  }

  private void loadFluidHeightMethodHandle() throws NoSuchMethodException, IllegalAccessException {
    Class<?> fluidClass = ReflectiveAccess.lookupServerClass("Fluid");
    fluidHeightMethodHandle = MethodHandles
      .lookup()
      .findVirtual(fluidClass, "d", MethodType.methodType(Float.TYPE));
  }

  private void loadFluidFlowMethodHandle() throws NoSuchMethodException, IllegalAccessException {
    Class<?> fluidClass = ReflectiveAccess.lookupServerClass("Fluid");
    Class<?> vector = ReflectiveAccess.lookupServerClass("Vec3D");
    Class<?> blockPosition = ReflectiveAccess.lookupServerClass("BlockPosition");
    Class<?> blockAccess = ReflectiveAccess.lookupServerClass("IBlockAccess");
    fluidFlowMethodHandle = MethodHandles
      .lookup()
      .findVirtual(fluidClass, "c", MethodType.methodType(vector, blockAccess, blockPosition));
  }

  private void loadWorldTypeMethodHandle() throws NoSuchMethodException, IllegalAccessException {
    Class<?> blockDataClass = ReflectiveAccess.lookupServerClass("IBlockData");
    Class<?> blockPositionClass = ReflectiveAccess.lookupServerClass("BlockPosition");
    MethodType methodType = MethodType.methodType(blockDataClass, blockPositionClass);
    worldTypeMethodHandle = MethodHandles
      .lookup()
      .findVirtual(ReflectiveAccess.NMS_WORLD_SERVER_CLASS, "getType", methodType);
  }

  private void loadFluidEmptyMethodHandle() throws NoSuchMethodException, IllegalAccessException {
    Class<?> fluidClass = ReflectiveAccess.lookupServerClass("Fluid");
    worldTypeMethodHandle = MethodHandles
      .lookup()
      .findVirtual(fluidClass, "isEmpty", MethodType.methodType(Boolean.TYPE));
  }

  @Override
  public boolean fluidStateEmpty(User user, double x, double y, double z) {
    int blockX = WrappedMathHelper.floor(x);
    int blockY = WrappedMathHelper.floor(y);
    int blockZ = WrappedMathHelper.floor(z);
    Object fluidState = fluidState(user, blockPositionOf(blockX, blockY, blockZ));
    try {
      return (boolean) worldTypeMethodHandle.invoke(fluidState);
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }

  private static final Class<?>[] BLOCK_POSITION_CONSTRUCTOR = new Class[]{Integer.TYPE, Integer.TYPE, Integer.TYPE};

  @Override
  public Object blockPositionOf(int x, int y, int z) {
    try {
      return blockPositionClass.getConstructor(BLOCK_POSITION_CONSTRUCTOR).newInstance(x, y, z);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new ReflectionFailureException(e);
    }
  }

  @Override
  public Object fluidState(User user, Object blockPosition) {
    UserMetaMovementData movementData = user.meta().movementData();
    Object world = movementData.nmsWorld();
    try {
      return fluidMethodHandle.invoke(world, blockPosition);
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }

  @Override
  public boolean fluidTaggedWithWater(Object fluidState) {
    try {
      return (boolean) fluidTaggedMethodHandle.invoke(fluidState, fluidTagWater);
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }

  @Override
  public float fluidHeight(Object fluidState) {
    try {
      return (float) fluidHeightMethodHandle.invoke(fluidState);
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }

  @Override
  public WrappedVector resolveFlowVector(Object fluidState, Object world, Object blockPosition) {
    try {
      Object vector = fluidFlowMethodHandle.invoke(fluidState, world, blockPosition);
      return WrappedVector.fromClass(vector);
    } catch (Throwable t) {
      throw new ReflectionFailureException(t);
    }
  }
}
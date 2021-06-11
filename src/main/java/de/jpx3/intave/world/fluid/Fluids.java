package de.jpx3.intave.world.fluid;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.tools.wrapper.WrappedMathHelper;
import de.jpx3.intave.user.User;
import org.bukkit.Location;

public final class Fluids {
  private static FluidEngine engine;

  public static void setup() {
    String className;
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      className = "de.jpx3.intave.world.fluid.resolver.v17FluidResolver";
    } else if (MinecraftVersions.VER1_16_0.atOrAbove()) {
      className = "de.jpx3.intave.world.fluid.resolver.v16FluidResolver";
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      className = "de.jpx3.intave.world.fluid.resolver.v14FluidResolver";
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      className = "de.jpx3.intave.world.fluid.resolver.v13FluidResolver";
    } else {
      className = "de.jpx3.intave.world.fluid.resolver.v12FluidResolver";
    }
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    try {
      engine = (FluidEngine) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
      throw new IntaveInternalException(e);
    }
  }

  public static boolean areEyesInFluid(User user, double positionX, double positionY, double positionZ) {
    return engine != null && engine.areEyesInFluid(user, positionX, positionY, positionZ);
  }

  public static boolean handleFluidAcceleration(User user, WrappedAxisAlignedBB boundingBox) {
    return engine != null && engine.handleFluidAcceleration(user, boundingBox);
  }

  public static WrappedFluid fluidAt(User user, int x, int y, int z) {
    return engine.fluidAt(user, x, y, z);
  }

  public static WrappedFluid fluidAt(User user, Location location) {
    return fluidAt(user, location.getX(), location.getY(), location.getZ());
  }

  public static WrappedFluid fluidAt(User user, double x, double y, double z) {
    return engine.fluidAt(user, WrappedMathHelper.floor(x), WrappedMathHelper.floor(y), WrappedMathHelper.floor(z));
  }

  public static boolean fluidStateEmpty(User user, double x, double y, double z) {
    return engine != null && fluidAt(user, x, y, z).empty();
  }
}

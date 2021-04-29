package de.jpx3.intave.world.waterflow;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.tools.wrapper.WrappedAxisAlignedBB;
import de.jpx3.intave.user.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Waterflow {
  private static AbstractWaterflowEngine engine;
  private static List<AbstractWaterflowEngine> availableEngines = new ArrayList<>();

  public static void setup() {
    registerEngine(NetherUpdateWaterflowEngine.class);
    registerEngine(BeeUpdateWaterflowEngine.class);
    registerEngine(VillageUpdateWaterflowEngine.class);
    registerEngine(AquaticUpdateWaterflowEngine.class);
    registerEngine(UnknownWaterflowEngine.class);

    selectAppropriateEngine();
  }

  private static void registerEngine(Class<? extends AbstractWaterflowEngine> engineClass) {
    AbstractWaterflowEngine engine;
    try {
      engine = engineClass.newInstance();
    } catch (InstantiationException | IllegalAccessException exception) {
      throw new IntaveInternalException(exception);
    }
    availableEngines.add(engine);
  }

  private static void selectAppropriateEngine() {
    MinecraftVersion currentVersion = ProtocolLibAdapter.serverVersion();
    engine = availableEngines.stream().filter(availableEngine -> availableEngine.appliesToAtLeast(currentVersion)).findFirst().orElse(engine);
    try {
      engine.setup();
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    availableEngines = Collections.emptyList();
  }

  public static boolean areEyesInFluid(User user, double positionX, double positionY, double positionZ) {
    return engine != null && engine.areEyesInFluid(user, positionX, positionY, positionZ);
  }

  public static boolean handleFluidAcceleration(User user, WrappedAxisAlignedBB boundingBox) {
    return engine != null && engine.handleFluidAcceleration(user, boundingBox);
  }

  public static boolean fluidStateEmpty(User user, double x, double y, double z) {
    return engine != null && engine.fluidStateEmpty(user, x, y, z);
  }
}

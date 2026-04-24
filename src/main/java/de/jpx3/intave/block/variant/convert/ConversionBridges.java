package de.jpx3.intave.block.variant.convert;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;

public final class ConversionBridges {
  private static final ConversionBridge conversionBridge;

  static {
    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    String className = "";
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      className = "de.jpx3.intave.block.variant.convert.PacketEventsConversionBridge";
    } else {
      className = "de.jpx3.intave.block.variant.convert.v8ConversionBridge";
    }
    Class<ConversionBridge> bridgeClass = PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, className);
    try {
      conversionBridge = bridgeClass.newInstance();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  public static ConversionBridge current() {
    return conversionBridge;
  }
}

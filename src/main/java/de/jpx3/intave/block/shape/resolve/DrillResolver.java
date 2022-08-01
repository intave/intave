package de.jpx3.intave.block.shape.resolve;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.shape.ShapeResolverPipeline;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_13_0;
import static de.jpx3.intave.adapter.MinecraftVersions.VER1_9_0;

final class DrillResolver {
  private static final ShapeResolverPipeline drill;

  static {
//    PatchyClassSwitchLoader<?> acbbResolver = PatchyClassSwitchLoader
//      .builderFor("de.jpx3.intave.block.shape.drill.acbbs.v{ver}AlwaysCollidingBoundingBox")
//      .withVersions(VER1_8_0, VER1_9_0, VER1_12_0)
//      .ignoreFrom(VER1_13_0)
//      .complete();
//
//    acbbResolver.loadIfAvailable();
//
//    PatchyClassSwitchLoader<ResolverPipeline> drillResolver = PatchyClassSwitchLoader
//      .<ResolverPipeline>builderFor("de.jpx3.intave.block.shape.shape.drill.v{ver}ShapeDrill")
//      .withVersions(VER1_8_0, VER1_9_0, VER1_12_0, VER1_13_0, VER1_14_0, VER1_17_1)
//      .complete();

    String drillClassName, acClassName = "";

    if (MinecraftVersions.VER1_17_1.atOrAbove()) {
      drillClassName = "de.jpx3.intave.block.shape.resolve.drill.v17b1ShapeDrill";
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.block.shape.resolve.drill.v14ShapeDrill";
    } else if (VER1_13_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.block.shape.resolve.drill.v13ShapeDrill";
    } else if (MinecraftVersions.VER1_11_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.block.shape.resolve.drill.v11ShapeDrill";
      acClassName = "de.jpx3.intave.block.shape.resolve.drill.acbbs.v11AlwaysCollidingBoundingBox";
    } else if (VER1_9_0.atOrAbove()) {
      drillClassName = "de.jpx3.intave.block.shape.resolve.drill.v9ShapeDrill";
      acClassName = "de.jpx3.intave.block.shape.resolve.drill.acbbs.v9AlwaysCollidingBoundingBox";
    } else {
      drillClassName = "de.jpx3.intave.block.shape.resolve.drill.v8ShapeDrill";
      acClassName = "de.jpx3.intave.block.shape.resolve.drill.acbbs.v8AlwaysCollidingBoundingBox";
    }

    ClassLoader classLoader = IntavePlugin.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, acClassName);
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, drillClassName);

    // server resolver
    drill = instanceOf(drillClassName);
  }

  public static ShapeResolverPipeline selectedDrill() {
    return drill;
  }

  private static <T> T instanceOf(String className) {
    try {
      //noinspection unchecked
      return (T) Class.forName(className).newInstance();
    } catch (InstantiationException | IllegalAccessException | ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }
}

package de.jpx3.intave.block.fluid;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.block.state.BlockStateCache;
import de.jpx3.intave.block.state.ExtendedBlockStateCache;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.user.User;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static de.jpx3.intave.adapter.MinecraftVersions.*;

public class Fluids {
  private static final Map<Material, Map<Integer, Fluid>> liquidData = new HashMap<>();
  private static FluidResolver resolver;
  private static FluidFlow v8Waterflow;
  private static FluidFlow v13Waterflow;

  public static void setup() {
    String className;
    if (VER1_18_2.atOrAbove()) {
      className = "de.jpx3.intave.block.fluid.v18b2FluidResolver";
    } else if (VER1_16_0.atOrAbove()) {
      className = "de.jpx3.intave.block.fluid.v16FluidResolver";
    } else if (VER1_15_0.atOrAbove()) {
      className = "de.jpx3.intave.block.fluid.v15FluidResolver";
    } else if (VER1_14_0.atOrAbove()) {
      className = "de.jpx3.intave.block.fluid.v14FluidResolver";
    } else if (VER1_13_0.atOrAbove()) {
      className = "de.jpx3.intave.block.fluid.v13FluidResolver";
    } else if (VER1_12_0.atOrAbove()) {
      className = "de.jpx3.intave.block.fluid.v12FluidResolver";
    } else {
      className = "de.jpx3.intave.block.fluid.v8FluidResolver";
    }
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    try {
      resolver = (FluidResolver) Class.forName(className).newInstance();
    } catch (Exception exception) {
      throw new IntaveInternalException(exception);
    }

    v8Waterflow = new v8Waterflow();
    v13Waterflow = new v13Waterflow();

    for (Material value : Material.values()) {
      if (value.isBlock()) {
        boolean anyLiquid = false;
        Map<Integer, Fluid> variants = new HashMap<>();
        for (int variantIndex : BlockVariantRegister.variantIdsOf(value)) {
          try {
            Fluid currentFluid = resolver.liquidFrom(value, variantIndex);
            variants.put(variantIndex, currentFluid);
            anyLiquid |= !currentFluid.isDry();
            if (currentFluid.isOfWater()) {
//              waterflow.add(value, variantIndex);
//              System.out.println("Added water " + value + ":" + variantIndex);
            }
          } catch (Exception exception) {
            BlockVariant properties = BlockVariantRegister.uncachedVariantOf(value, variantIndex);
            String propertyString = "{"+properties.propertyNames().stream().map(s -> s + ": " + properties.propertyOf(s)).collect(Collectors.joining(", ")) +"}";
            IntaveLogger.logger().error("Failed to index fluid " + value + ":" + variantIndex + " " + propertyString);
            exception.printStackTrace();
          }
        }
        if (anyLiquid) {
          liquidData.put(value, variants);
        }
      }
    }
  }

  public static FluidFlow suitableWaterflowFor(User user) {
    return user.meta().protocol().waterUpdate() ? v13Waterflow : v8Waterflow;
  }

  public static FluidFlow anyWaterflow() {
    return v8Waterflow;
  }

  public static boolean canContainFluid(Material material) {
    return liquidData.containsKey(material);
  }

  public static boolean isFluid(Material material, int variantIndex) {
    Map<Integer, Fluid> liquidMappings = liquidData.get(material);
    return liquidMappings != null && liquidMappings.containsKey(variantIndex)
      && !liquidMappings.get(variantIndex).isDry();
  }

  public static @NotNull Fluid fluidStateOf(Material material, int variant) {
    Map<Integer, Fluid> map = liquidData.get(material);
    if (map == null) {
      return Dry.of();
    }
    Fluid fluid = map.get(variant);
    if (fluid == null) {
      return Dry.of();
    }
    return fluid;
  }

  public static @NotNull Fluid fluidAt(User user, Position position) {
    return fluidAt(user, position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  @Deprecated
  // use VolatileBlockAccess instead
  public static @NotNull Fluid fluidAt(User user, Location location) {
    return fluidAt(user, location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  @Deprecated
  // use VolatileBlockAccess instead
  public static @NotNull Fluid fluidAt(User user, BlockPosition position) {
    return fluidAt(user, position.getX(), position.getY(), position.getZ());
  }

  @Deprecated
  // use VolatileBlockAccess instead
  public static @NotNull Fluid fluidAt(User user, double x, double y, double z) {
    return fluidAt(user, floor(x), floor(y), floor(z));
  }

  @Deprecated
  // use VolatileBlockAccess instead
  public static @NotNull Fluid fluidAt(User user, int x, int y, int z) {
    BlockStateCache states = user.blockStates();
    Material type = states.typeAt(x, y, z);
    Map<Integer, Fluid> stateMap = liquidData.get(type);
    if (stateMap == null) {
      return Dry.of();
    }
    int variant = states.variantIndexAt(x, y, z);
    Fluid fluid = stateMap.get(variant);
    if (fluid == null) {
      return Dry.of();
    }
    return fluid;
  }

  public static boolean fluidPresentAt(User user, Position position) {
    return fluidPresentAt(user, position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  public static boolean fluidPresentAt(User user, Location location) {
    return fluidPresentAt(user, location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  public static boolean fluidPresentAt(User user, BlockPosition position) {
    return fluidPresentAt(user, position.getX(), position.getY(), position.getZ());
  }

  public static boolean fluidPresentAt(User user, double x, double y, double z) {
    return fluidPresentAt(user, floor(x), floor(y), floor(z));
  }

  public static boolean fluidPresentAt(User user, int x, int y, int z) {
    ExtendedBlockStateCache states = user.blockStates();
    Material type = states.typeAt(x, y, z);
    Map<Integer, Fluid> stateMap = liquidData.get(type);
    if (stateMap == null) {
      return false;
    }
    int variant = states.variantIndexAt(x, y, z);
    Fluid fluid = stateMap.get(variant);
    if (fluid == null) {
      return false;
    }
    return !fluid.isDry();
  }

  private static int floor(double value) {
    int i = (int) value;
    return value < (double) i ? i - 1 : i;
  }
}

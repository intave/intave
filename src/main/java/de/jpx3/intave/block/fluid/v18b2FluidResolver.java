package de.jpx3.intave.block.fluid;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.IBlockData;
import org.bukkit.Material;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

@PatchyAutoTranslation
final class v18b2FluidResolver implements FluidResolver {
  private static Object TAG_KEY_WATER = null;
  private static Object TAG_KEY_LAVA = null;

  private static final MethodHandle resolveTagKey;

  static {
    try {
      TAG_KEY_WATER = Lookup.serverField("TagsFluid", "WATER").get(null);
      TAG_KEY_LAVA = Lookup.serverField("TagsFluid", "LAVA").get(null);
    } catch (IllegalAccessException exception) {
      exception.printStackTrace();
    }
    resolveTagKey = MethodSearchBySignature
      .ofClass(Lookup.serverClass("Fluid"))
      .withParameters(new Class[]{Lookup.serverClass("TagKey")})
      .withReturnType(Boolean.TYPE).search().findFirstOrThrow();
  }

  private static Method fluidAccessMethod;

  @Override
  @PatchyAutoTranslation
  public Fluid liquidFrom(Material type, int variantIndex) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variantIndex);
    if (blockData == null) {
      return Dry.of();
    }
    try {
      net.minecraft.world.level.material.Fluid fluid;
      if (MinecraftVersions.VER1_21.atOrAbove()) {
        Block block = blockData.getBlock();
        if (fluidAccessMethod == null) {
          fluidAccessMethod = Lookup.serverClass("BlockBase").getDeclaredMethod("getFluidState", IBlockData.class);
          fluidAccessMethod.setAccessible(true);
        }
        fluid = (net.minecraft.world.level.material.Fluid) fluidAccessMethod.invoke(block, blockData);
      } else {
        fluid = blockData.getBlock().c_(blockData);
      }

      boolean dry = fluid.isEmpty();
      boolean isWater = !dry && (boolean) resolveTagKey.invoke(fluid, TAG_KEY_WATER);
      boolean isLava = !dry && (boolean) resolveTagKey.invoke(fluid, TAG_KEY_LAVA);
      boolean source = fluid.isSource();
      float height = fluid.d();
      BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
      Boolean fallingProperty = dry ? null : variant.propertyOf("falling");
      int level = MaterialMagic.isLavaOrWater(type) ? variant.propertyOf("level") : 8;
      if (fallingProperty == null) {
        fallingProperty = !dry && MaterialMagic.isLavaOrWater(type) && level >= 8;
      }
      return select(isWater, isLava, dry, fallingProperty, height, level);
    } catch (Throwable throwable) {
      throwable.printStackTrace();
      return Dry.of();
    }
  }
}

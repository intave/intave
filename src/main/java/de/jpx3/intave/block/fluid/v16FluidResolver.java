package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_16_R3.IBlockData;
import net.minecraft.server.v1_16_R3.TagsFluid;
import org.bukkit.Material;

@PatchyAutoTranslation
final class v16FluidResolver implements FluidResolver {
  @Override
  @PatchyAutoTranslation
  public Fluid liquidFrom(Material type, int variantIndex) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variantIndex);
    if (blockData == null) {
      return Dry.of();
    }
    net.minecraft.server.v1_16_R3.Fluid fluid = blockData.getFluid();
    if (fluid == null) {
      return Dry.of();
    }
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    boolean dry = fluid.isEmpty();
    boolean isWater = fluid.a(TagsFluid.WATER);
    boolean isLava = fluid.a(TagsFluid.LAVA);
    boolean source = fluid.isSource();
    int level = fluid.e();
    Boolean fallingProperty = dry ? null : variant.propertyOf("falling");
    if (fallingProperty == null) {
      fallingProperty = !dry && MaterialMagic.isLavaOrWater(type) && level >= 8;
    }
    float height = level / 9f;
    return select(isWater, isLava, dry, fallingProperty, height, level);
  }
}


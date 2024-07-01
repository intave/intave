package de.jpx3.intave.block.fluid;

import de.jpx3.intave.block.physics.MaterialMagic;
import de.jpx3.intave.block.variant.BlockVariant;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_14_R1.IBlockData;
import net.minecraft.server.v1_14_R1.TagsFluid;
import org.bukkit.Material;

@PatchyAutoTranslation
final class v14FluidResolver implements FluidResolver {
  @Override
  @PatchyAutoTranslation
  public Fluid liquidFrom(Material type, int variantIndex) {
    IBlockData blockData = (IBlockData) BlockVariantRegister.rawVariantOf(type, variantIndex);
    if (blockData == null) {
      return Dry.of();
    }
    net.minecraft.server.v1_14_R1.Fluid fluid = blockData.p();
    if (fluid == null) {
      return Dry.of();
    }
    BlockVariant variant = BlockVariantRegister.variantOf(type, variantIndex);
    boolean dry = fluid.isEmpty();
    boolean isWater = fluid.a(TagsFluid.WATER);
    boolean isLava = fluid.a(TagsFluid.LAVA);
    boolean source = fluid.isSource();
    Boolean fallingProperty = dry ? null : variant.propertyOf("falling");
    int level = MaterialMagic.isLavaOrWater(type) ? variant.propertyOf("level") : 8;
    if (fallingProperty == null) {
      fallingProperty = !dry && MaterialMagic.isLavaOrWater(type) && level >= 8;
    }
    float height = fluid.f();
    return select(isWater, isLava, dry, fallingProperty, height, level);
  }
}

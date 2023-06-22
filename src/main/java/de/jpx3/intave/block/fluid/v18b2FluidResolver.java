package de.jpx3.intave.block.fluid;

import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyTranslateParameters;
import de.jpx3.intave.share.NativeVector;
import de.jpx3.intave.share.link.WrapperConverter;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import net.minecraft.core.BlockPosition;
import net.minecraft.world.level.IBlockAccess;
import net.minecraft.world.level.World;

import java.lang.invoke.MethodHandle;

@PatchyAutoTranslation
final class v18b2FluidResolver extends FluidResolver {
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

  @Override
  @PatchyAutoTranslation
  protected Fluid fluidAt(User user, int x, int y, int z) {
    MovementMetadata movementData = user.meta().movement();
    World world = (World) movementData.nmsWorld();
    if (world == null) {
      return Fluid.empty();
    }
    IBlockAccess blockAccess = world.getChunkProvider().c(x >> 4, z >> 4);
    if (blockAccess == null) {
      return Fluid.empty();
    }
    net.minecraft.world.level.material.Fluid fluid = blockAccess.getFluid(new BlockPosition(x, y, z));
    FluidTag fluidTag = resolveFluidTagOf(fluid);
    if (fluidTag == FluidTag.EMPTY) {
      return Fluid.empty();
    }
    float height = fluid.d();
    return Fluid.of(fluidTag, fluid.isSource(), height);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private FluidTag resolveFluidTagOf(net.minecraft.world.level.material.Fluid fluid) {
    return fluid.isEmpty() ? FluidTag.EMPTY : tagKeyResolve(fluid);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private FluidTag tagKeyResolve(Object fluid) {
    try {
      boolean water = (boolean) resolveTagKey.invoke(fluid, TAG_KEY_WATER);
      boolean lava = !water && (boolean) resolveTagKey.invoke(fluid, TAG_KEY_LAVA);
      return FluidTag.select(water, lava);
    } catch (Throwable exception) {
      exception.printStackTrace();
    }
    return FluidTag.EMPTY;
  }

  @Override
  @PatchyAutoTranslation
  protected NativeVector flowVectorAt(User user, int x, int y, int z) {
    MovementMetadata movementData = user.meta().movement();
    World world = (World) movementData.nmsWorld();
    IBlockAccess blockAccess = world.getChunkProvider().c(x >> 4, z >> 4);
    if (blockAccess == null) {
      return NativeVector.ZERO;
    }
    BlockPosition blockPosition = new BlockPosition(x, y, z);
    return WrapperConverter.vectorFromVec3D(blockAccess.getFluid(blockPosition).c(blockAccess, blockPosition));
  }
}

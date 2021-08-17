package de.jpx3.intave.world.fluid.resolver;

import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.reflect.patchy.annotate.PatchyTranslateParameters;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.MovementMetadata;
import de.jpx3.intave.world.fluid.FluidEngine;
import de.jpx3.intave.world.fluid.FluidTag;
import de.jpx3.intave.world.fluid.WrappedFluid;
import de.jpx3.intave.world.wrapper.WrappedVector;
import de.jpx3.intave.world.wrapper.link.WrapperLinkage;
import net.minecraft.server.v1_13_R2.*;

@PatchyAutoTranslation
public final class v13FluidResolver extends FluidEngine {
  @Override
  @PatchyAutoTranslation
  protected WrappedFluid fluidAt(User user, int x, int y, int z) {
    MovementMetadata movementData = user.meta().movement();
    World world = (World) movementData.nmsWorld();
    if (!world.isChunkLoaded(x >> 4, z >> 4, false)) {
      return WrappedFluid.empty();
    }
    Fluid fluid = world.getFluid(new BlockPosition(x, y, z));
    FluidTag fluidTag = resolveFluidTagOf(fluid);
    if (fluidTag == FluidTag.EMPTY) {
      return WrappedFluid.empty();
    }
    float height = fluid.getHeight();
    return WrappedFluid.construct(fluidTag, height);
  }

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  private FluidTag resolveFluidTagOf(Fluid fluid) {
    if (fluid.e()) {
      return FluidTag.EMPTY;
    }
    //noinspection unchecked
    if (fluid.a((Tag<FluidType>) FluidTag.WATER.nativeTag())) {
      return FluidTag.WATER;
    }
    //noinspection unchecked
    if (fluid.a((Tag<FluidType>) FluidTag.LAVA.nativeTag())) {
      return FluidTag.LAVA;
    }
    return FluidTag.EMPTY;
  }

  @Override
  @PatchyAutoTranslation
  protected WrappedVector flowVectorAt(User user, int x, int y, int z) {
    MovementMetadata movementData = user.meta().movement();
    IWorldReader world = (World) movementData.nmsWorld();
    BlockPosition blockPosition = new BlockPosition(x, y, z);
    return WrapperLinkage.vectorOf(world.getFluid(blockPosition).a(world, blockPosition));
  }
}
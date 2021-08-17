package de.jpx3.intave.world.wrapper.link;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.wrapper.WrappedBlockPosition;
import net.minecraft.server.v1_8_R3.BlockPosition;

public final class BlockPositionLinkage {
  static ClassLinker<WrappedBlockPosition> resolveBlockPositionLinker() {
    String boundingBoxResolverClass = "de.jpx3.intave.world.wrapper.link.BlockPositionLinkage$BlockPositionResolver";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), boundingBoxResolverClass);
    return new BlockPositionResolver();
  }

  @PatchyAutoTranslation
  public static final class BlockPositionResolver implements ClassLinker<WrappedBlockPosition> {
    @PatchyAutoTranslation
    @Override
    public WrappedBlockPosition link(Object obj) {
      BlockPosition blockPosition = (BlockPosition) obj;
      return new WrappedBlockPosition(blockPosition.getX(), blockPosition.getY(), blockPosition.getZ());
    }
  }
}
package de.jpx3.intave.reflect;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.Block;

public final class ReflectiveBlockAccess {
  private static BlockAccess blockAccess;

  static void setup() {
    boolean useNewResolver = ProtocolLibAdapter.AQUATIC_UPDATE.atOrAbove();
    String className = useNewResolver
      ? "de.jpx3.intave.reflect.ReflectiveBlockAccess$BlockAccessNew"
      : "de.jpx3.intave.reflect.ReflectiveBlockAccess$BlockAccessLegacy";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    blockAccess = useNewResolver ? new BlockAccessNew() : new BlockAccessLegacy();
  }

  @Deprecated
  public static Object blockById(int blockId) {
    return blockAccess.resolveById(blockId);
  }

  private interface BlockAccess {
    Object resolveById(int blockId);
  }

  @PatchyAutoTranslation
  private static final class BlockAccessLegacy implements BlockAccess {
    @PatchyAutoTranslation
    @Override
    public Object resolveById(int blockId) {
      return Block.getById(blockId);
    }
  }

  @PatchyAutoTranslation
  private static final class BlockAccessNew implements BlockAccess {
    @PatchyAutoTranslation
    @Override
    public Object resolveById(int blockId) {
      return net.minecraft.server.v1_13_R2.Block.getByCombinedId(blockId).getBlock();
    }
  }
}
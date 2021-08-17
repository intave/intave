package de.jpx3.intave.reflect.access;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.Block;

public final class ReflectiveBlockAccess {
  private static BlockAccess blockAccess;

  static void setup() {
    boolean useNewResolver = MinecraftVersions.VER1_13_0.atOrAbove();
    String className = useNewResolver
      ? "de.jpx3.intave.reflect.access.ReflectiveBlockAccess$BlockAccessNew"
      : "de.jpx3.intave.reflect.access.ReflectiveBlockAccess$BlockAccessLegacy";
    PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
    blockAccess = useNewResolver ? new BlockAccessNew() : new BlockAccessLegacy();
  }

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
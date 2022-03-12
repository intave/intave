package de.jpx3.intave.world.chunk;

import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_9_R2.CraftWorld;

public final class ChunkProviderServerAccess {
  private final static boolean MODERN_ACCESS = MinecraftVersions.VER1_9_0.atOrAbove();

  public static void setup() {
    String name;
    if (MODERN_ACCESS) {
      name = "de.jpx3.intave.world.chunk.ChunkProviderServerAccess$ModernAccess";
    } else {
      name = "de.jpx3.intave.world.chunk.ChunkProviderServerAccess$LegacyAccess";
    }
    ClassLoader classLoader = ChunkProviderServerAccess.class.getClassLoader();
    PatchyLoadingInjector.loadUnloadedClassPatched(classLoader, name);
  }

  public static Object chunkProviderServerOf(World world) {
    return MODERN_ACCESS ? ModernAccess.chunkProviderServerOf(world) : LegacyAccess.chunkProviderServerOf(world);
  }

  @PatchyAutoTranslation
  static class LegacyAccess {
    @PatchyAutoTranslation
    private static Object chunkProviderServerOf(org.bukkit.World world) {
      return ((org.bukkit.craftbukkit.v1_8_R3.CraftWorld) world).getHandle().chunkProviderServer;
    }
  }

  @PatchyAutoTranslation
  static class ModernAccess {
    @PatchyAutoTranslation
    private static Object chunkProviderServerOf(org.bukkit.World world) {
      return ((CraftWorld) world).getHandle().getChunkProvider();
    }
  }
}

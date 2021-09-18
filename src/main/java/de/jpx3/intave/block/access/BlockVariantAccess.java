package de.jpx3.intave.block.access;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

@Deprecated
public final class BlockVariantAccess {
  private final static boolean MODERN_MATERIAL_PROCESSING = MinecraftVersions.VER1_13_0.atOrAbove();

  public static void setup() {
  }

  /**
   * This method performs a direct type lookup, which will be quite heavy if the underlying chunk has not been loaded yet.
   * To avoid this performance-bottleneck, use {@link VolatileBlockAccess#variantIndexAccess(User, World, double, double, double)} instead,
   * providing fast performance, a robust cache implementation and stable chunk fallback
   */
  @Deprecated
  public static int variantAccess(Block block) {
    return BlockAccess.global().variantIndexOf(block);
  }

  public static int variantAccess(WrappedBlockData blockData) {
    if(!MODERN_MATERIAL_PROCESSING) {
      return blockData.getData();
    }
    Material type = blockData.getType();
    Object handle = blockData.getHandle();
    int index = BlockVariantRegister.variantIndexOf(type, handle);
    if (index < 0) {
      throw new IllegalStateException("Invalid block data update: " + type + "/" + handle);
    }
    return index;
  }

  public static Object nativeVariantAccess(Block bukkitBlock) {
    return BlockAccess.global().nativeVariantOf(bukkitBlock);
  }
}

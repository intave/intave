package de.jpx3.intave.block.variant;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.block.access.BlockAccess;
import de.jpx3.intave.block.access.VolatileBlockAccess;
import de.jpx3.intave.user.User;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

public final class BlockVariantNativeAccess {
  private static final boolean MODERN_MATERIAL_PROCESSING = MinecraftVersions.VER1_13_0.atOrAbove();

  public static void setup() {
  }

  /**
   * This method performs a direct type lookup, which will be quite heavy if the underlying chunk has not been loaded yet.
   * To avoid this performance-bottleneck, use {@link VolatileBlockAccess#variantIndexAccess(User, World, double, double, double)} instead,
   * providing fast performance, a robust cache implementation and stable chunk fallback
   */
  @Deprecated
  public static int variantAccess(Block block) {
    if (!MODERN_MATERIAL_PROCESSING) {
      return BlockAccess.global().variantIndexOf(block);
    }
    int index = BlockVariantRegister.variantIndexOf(block.getType(), nativeVariantAccess(block));
    return Math.max(index, 0);
  }

  public static int variantAccess(WrappedBlockState blockState) {
    if (!MODERN_MATERIAL_PROCESSING) {
      return SpigotConversionUtil.toBukkitMaterialData(blockState).getData();
    }
    Material type = SpigotConversionUtil.toBukkitBlockData(blockState).getMaterial();
    Object nativeBlockData = blockState;
    int index = BlockVariantRegister.variantIndexOf(type, nativeBlockData);
    if (index < 0) {
      throw new IllegalStateException("Invalid block data update: " + type + "/" + blockState);
    }
    return index;
  }

  public static Object nativeVariantAccess(Block bukkitBlock) {
    if (MODERN_MATERIAL_PROCESSING) {
      return nativeVariantAccess(blockDataOf(bukkitBlock));
    }
    return BlockAccess.global().nativeVariantOf(bukkitBlock);
  }

  private static Object nativeVariantAccess(BlockData blockData) {
    return SpigotConversionUtil.fromBukkitBlockData(blockData);
  }

  private static BlockData blockDataOf(Block block) {
    try {
      return (BlockData) block.getClass().getMethod("getBlockData").invoke(block);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Unable to resolve Bukkit block data for " + block.getType(), exception);
    }
  }
}

package de.jpx3.intave.world.blockaccess;

import com.comphenix.protocol.wrappers.WrappedBlockData;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.Lookup;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public final class BlockVariantAccess {
  private static MethodHandle nativeBlockDataAccess;
  private static MethodHandle nativeBlockDataExtractionAccess;

  private final static boolean NEW_BLOCK_ACCESS = MinecraftVersions.VER1_13_0.atOrAbove();
  private final static boolean MODERN_MATERIAL_PROCESSING = MinecraftVersions.VER1_14_0.atOrAbove();

  public static void setup() {
    try {
      if (NEW_BLOCK_ACCESS) {
        Class<?> blockDataClass = Lookup.serverClass("IBlockData");
        Class<?> craftBukkitClass = Lookup.craftBukkitClass("block.CraftBlock");
        nativeBlockDataAccess = MethodHandles.lookup().findVirtual(craftBukkitClass, "getNMS", MethodType.methodType(blockDataClass));
      } else {
        Class<?> blockClass = Lookup.serverClass("Block");
        Class<?> blockDataClass = Lookup.serverClass("IBlockData");
        Class<?> craftBukkitClass = Lookup.craftBukkitClass("block.CraftBlock");
        Method getNMSBlockMethod = craftBukkitClass.getDeclaredMethod("getNMSBlock");
        getNMSBlockMethod.setAccessible(true);
        nativeBlockDataAccess = MethodHandles.lookup().unreflect(getNMSBlockMethod);
        nativeBlockDataExtractionAccess = MethodHandles.lookup().findVirtual(blockClass, "fromLegacyData", MethodType.methodType(blockDataClass, Integer.TYPE));
      }
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new IntaveInternalException("Failed to load data accessor", exception);
    }
  }

  /**
   * This method performs a direct type lookup, which will be quite heavy if the underlying chunk has not been loaded yet.
   * To avoid this performance-bottleneck, use {@link BukkitBlockAccess#cacheAppliedVariantAccess(User, World, double, double, double)} instead,
   * providing fast performance, a robust cache implementation and stable chunk fallback
   */
  @Deprecated
  public static int variantAccess(Block block) {
    return BlockAccessProvider.accessor().variantOf(block);
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

  public static Object nativeBlockDataOf(Block bukkitBlock) {
    return BlockAccessProvider.accessor().blockHandle(bukkitBlock);
  }
}

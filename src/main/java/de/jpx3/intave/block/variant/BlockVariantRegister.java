package de.jpx3.intave.block.variant;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.block.variant.index.VariantIndex;
import de.jpx3.intave.cleanup.ReferenceMap;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BlockVariantRegister {
  private static final Map<Material, Map<Object, Integer>> blockDataIndex = new EnumMap<>(Material.class);
  private static final Map<Material, Map<Integer, Object>> blockDataRegister = new EnumMap<>(Material.class);
  private static final Map<Material, Map<Integer, BlockVariant>> blockVariants = ReferenceMap.soft(new EnumMap<>(Material.class));

  public static void index() {
    for (Material type : Material.values()) {
      if (type.isBlock()) {
        VariantIndex.indexApplyWithReverse(type, blockDataIndex::put, blockDataRegister::put);
      }
    }
    int count = 0;
    int blockCount = 0;
    for (Map<Object, Integer> index : blockDataIndex.values()) {
      count += index.size();
      blockCount++;
    }
    if (IntaveControl.DEBUG_VARIANT_COMPILATION) {
      System.out.println("[variant/debug] Indexed " + count + " variations of " + blockCount + " blocks");
    }
  }

  static final BlockVariant EMPTY_ERROR = new EmptyBlockVariant();

  // Note: Caching all materials can become quite memory-intensive.
  //       Only pass in materials that are actually used, always filter random materials!
  public static BlockVariant variantOf(Material type, int variantIndex) {
    Map<Integer, BlockVariant> variantMap = blockVariants.computeIfAbsent(type, BlockVariantRegister::translateFromServer);
    BlockVariant variant = variantMap.get(variantIndex);
    if (variant == null) {
      IntaveLogger.logger().error("Could not find variant for " + type + " with index " + variantIndex + " (map size: " + variantMap.size() + ")");
      return EMPTY_ERROR;
    }
    return variant;
  }

  private static Map<Integer, BlockVariant> translateFromServer(Material material) {
    Map<Integer, BlockVariant> map = BlockVariantConverter.translateSettings(material, blockDataRegister.get(material));
    if (IntaveControl.DEBUG_VARIANT_COMPILATION) {
      System.out.println("[variant/debug] Compiled " + map.size() + " variants for " + material);
      System.out.println("[variant/debug] Zero variant is: ");
      map.get(0).dumpStates();
    }
    return map;
  }

  public static int variantIndexOf(Material type, Object rawBlockData) {
    Map<Object, Integer> indexMap = blockDataIndex.get(type);
    Integer integer = indexMap.get(rawBlockData);
    return integer == null ? -1 : integer;
  }

  public static Object rawVariantOf(Material type, int variantIndex) {
    try {
      return blockDataRegister.get(type).get(variantIndex);
    } catch (Exception exception) {
      IntaveLogger.logger().printLine("[Intave] Failed to correctly emulate data structure of block type " + type + " (requested variant " + variantIndex + ")");
      exception.printStackTrace();
      return blockDataRegister.get(type).get(0);
    }
  }

  public static Set<Integer> variantIdsOf(Material type) {
    return new HashSet<>(blockDataRegister.get(type).keySet());
  }

  static void invalidateShadowedVariantCache() {
    blockVariants.clear();
  }
}

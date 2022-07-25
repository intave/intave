package de.jpx3.intave.block.variant;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.block.variant.index.VariantIndex;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

public final class BlockVariantRegister {
  private static final Map<Material, Map<Object, Integer>> blockDataIndex = new EnumMap<>(Material.class);
  private static final Map<Material, Map<Integer, Object>> blockDataRegister = new EnumMap<>(Material.class);
  private static final Map<Material, Map<Integer, BlockVariant>> blockVariants = new EnumMap<>(Material.class);

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
    IntaveLogger.logger().info("Indexed " + count + " variations of " + blockCount + " blocks");
  }

  public static BlockVariant variantOf(Material type, int variantIndex) {
    return blockVariants.computeIfAbsent(type, material ->
      BlockVariantConverter.translate(material, blockDataRegister.get(material))
    ).get(variantIndex);
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
}

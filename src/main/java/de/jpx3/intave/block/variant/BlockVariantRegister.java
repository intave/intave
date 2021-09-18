package de.jpx3.intave.block.variant;

import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.rewrite.PatchyAutoTranslation;
import de.jpx3.intave.clazz.rewrite.PatchyLoadingInjector;
import net.minecraft.server.v1_14_R1.Block;
import net.minecraft.server.v1_14_R1.IBlockData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class BlockVariantRegister {
  private final static boolean REGISTER_ACTIVE = MinecraftVersions.VER1_13_0.atOrAbove();
  private final static Map<Material, Map<Object, Integer>> blockDataIndex = new EnumMap<>(Material.class);
  private final static Map<Material, Map<Integer, Object>> blockDataRegister = new EnumMap<>(Material.class);
  private final static Map<Material, Map<Integer, BlockVariant>> blockVariants = new EnumMap<>(Material.class);

  static {
    if (REGISTER_ACTIVE) {
      PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.block.variant.BlockVariantRegister$Indexer");
    }
  }

  public static void indexIfAvailable() {
    if (!REGISTER_ACTIVE) {
      return;
    }
    Arrays.stream(Material.values())
      .filter(Material::isBlock)
      .forEach(type -> Indexer.index(type, blockDataIndex::put, blockDataRegister::put));
  }

  public static BlockVariant variantOf(Material type, int variantIndex) {
    return blockVariants.computeIfAbsent(type, material -> {
      Map<Integer, Object> variantIndexRegister = blockDataRegister.get(material);
      return BlockVariantConverter.translate(material, variantIndexRegister);
    }).get(variantIndex);
  }

  public static int variantIndexOf(Material type, Object rawBlockData) {
    Map<Object, Integer> indexMap = blockDataIndex.get(type);
    Integer integer = indexMap.get(rawBlockData);
    if (integer == null) {
      return -1;
    }
    return integer;
  }

  public static Object rawBlockDataOf(Material type, int variantIndex) {
    try {
      return blockDataRegister.get(type).get(variantIndex);
    } catch (Exception exception) {
      IntaveLogger.logger().printLine("[Intave] Failed to correctly emulate data structure of block type " + type + " (requested variant " + variantIndex + ")");
      exception.printStackTrace();
      return blockDataRegister.get(type).get(0);
    }
  }

  @PatchyAutoTranslation
  private static class Indexer {
    private final static boolean MODERN_INDEX = MinecraftVersions.VER1_14_0.atOrAbove();

    @PatchyAutoTranslation
    public static void index(
      Material type,
      BiConsumer<Material, Map<Object, Integer>> indexApply,
      BiConsumer<Material, Map<Integer, Object>> registerApply
    ) {
      if (MODERN_INDEX) {
        modernIndex(type, indexApply, registerApply);
      } else {
        legacyIndex(type, indexApply, registerApply);
      }
    }

    @PatchyAutoTranslation
    public static void modernIndex(
      Material type,
      BiConsumer<Material, Map<Object, Integer>> indexApply,
      BiConsumer<Material, Map<Integer, Object>> registerApply
    ) {
      CraftBlockData blockData = CraftBlockData.newData(type, null);
      Block block = blockData.getState().getBlock();
      Map<Object, Integer> index = new HashMap<>();
      Map<Integer, Object> register = new HashMap<>();
      int id = 0;
      for (IBlockData nativeState : block.getStates().a()) {
        index.put(nativeState, id);
        register.put(id, nativeState);
        id++;
      }
      indexApply.accept(type, index);
      registerApply.accept(type, register);
    }

    @PatchyAutoTranslation
    public static void legacyIndex(
      Material type,
      BiConsumer<Material, Map<Object, Integer>> indexApply,
      BiConsumer<Material, Map<Integer, Object>> registerApply
    ) {
      org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData blockData = org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData.newData(type, null);
      net.minecraft.server.v1_13_R2.Block block = blockData.getState().getBlock();
      Map<Object, Integer> index = new HashMap<>();
      Map<Integer, Object> register = new HashMap<>();
      int id = 0;
      for (net.minecraft.server.v1_13_R2.IBlockData nativeState : block.getStates().a()) {
        index.put(nativeState, id);
        register.put(id, nativeState);
        id++;
      }
      indexApply.accept(type, index);
      registerApply.accept(type, register);
    }
  }
}

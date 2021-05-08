package de.jpx3.intave.world.blockaccess;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.logging.IntaveLogger;
import de.jpx3.intave.patchy.PatchyLoadingInjector;
import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import net.minecraft.server.v1_14_R1.Block;
import net.minecraft.server.v1_14_R1.IBlockData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class RuntimeBlockDataIndexer {
  private final static boolean required = MinecraftVersions.VER1_14_0.atOrAbove();
  private final static Map<Material, Map<Object, Integer>> blockDataIndex = new EnumMap<>(Material.class);
  private final static Map<Material, List<Object>> blockDataRegister = new EnumMap<>(Material.class);

  static {
    if (required) {
      PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.world.blockaccess.RuntimeBlockDataIndexer$Indexer");
    }
  }

  public static void prepareIndex() {
    if (!required) return;
    Arrays.stream(Material.values())
      .filter(Material::isBlock)
      .forEach(type -> Indexer.index(type, blockDataIndex::put, blockDataRegister::put));
    AtomicInteger blocks = new AtomicInteger(), artificialIds = new AtomicInteger();
    blockDataIndex.forEach((material, objectIntegerMap) -> {
      blocks.incrementAndGet();
      artificialIds.addAndGet(objectIntegerMap.size());
    });
    String message = "Indexed " + artificialIds.get() + " new block-data keys for " + blocks.get() + " blocks";
    IntaveLogger.logger().info(message);
  }

  public static int indexOfModernState(Material type, Object rawBlockData) {
    return blockDataIndex.get(type).get(rawBlockData);
  }

  public static Object modernStateFromIndex(Material type, int blockState) {
    try {
      return blockDataRegister.get(type).get(blockState);
    } catch (Exception exception) {
      System.out.println("[Intave] Failed to correctly emulate data structure of block type " + type + " (requested state " + blockState + ")");
      exception.printStackTrace();
      return blockDataRegister.get(type).get(0);
    }
  }

  @PatchyAutoTranslation
  private static class Indexer {
    @PatchyAutoTranslation
    public static void index(
      Material type,
      BiConsumer<Material, Map<Object, Integer>> indexApply,
      BiConsumer<Material, List<Object>> registerApply
    ) {
      CraftBlockData blockData = CraftBlockData.newData(type, null);
      Block block = blockData.getState().getBlock();
      ImmutableList<IBlockData> nativeStates = block.getStates().a();
      Map<Object, Integer> index = new HashMap<>();
      List<Object> register = new ArrayList<>();
      int i = 0;
      for (IBlockData nativeState : nativeStates) {
        index.put(nativeState, i++);
        register.add(nativeState);
      }
      indexApply.accept(type, index);
      registerApply.accept(type, register);
    }
  }
}

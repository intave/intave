package de.jpx3.intave.world.blockaccess;

import com.google.common.collect.ImmutableList;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.reflect.patchy.PatchyLoadingInjector;
import de.jpx3.intave.reflect.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.world.state.BlockState;
import net.minecraft.server.v1_14_R1.Block;
import net.minecraft.server.v1_14_R1.IBlockData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class RuntimeBlockDataIndexer {
  private final static boolean required = MinecraftVersions.VER1_14_0.atOrAbove();
  private final static Map<Material, Map<Object, Integer>> blockDataIndex = new EnumMap<>(Material.class);
  private final static Map<Material, Map<Integer, Object>> blockDataRegister = new EnumMap<>(Material.class);
  private final static Map<Material, Map<Integer, BlockState>> blockStates = new EnumMap<>(Material.class);

  static {
    if (required) {
      PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), "de.jpx3.intave.world.blockaccess.RuntimeBlockDataIndexer$Indexer");
    }
  }

  public static void prepareIndex() {
    if (!required) return;
    Arrays.stream(Material.values())
      .filter(Material::isBlock)
      .forEach(type -> Indexer.index(type, blockDataIndex::put, blockDataRegister::put, blockStates::put));
  }

  public static Iterable<Object> variantsOfType(Material material) {
    return ImmutableList.copyOf(blockDataIndex.get(material).keySet());
  }

  public static int indexOfModernState(Material type, Object rawBlockData) {
    Map<Object, Integer> indexMap = blockDataIndex.get(type);
    Integer integer = indexMap.get(rawBlockData);
    if (integer == null) {
      return -1;
//      throw new IllegalStateException("Unable to find block " + type + "/" + rawBlockData);
    }
    return integer;
  }

  public static Object modernStateFromIndex(Material type, int blockState) {
    try {
      return blockDataRegister.get(type).get(blockState);
    } catch (Exception exception) {
      IntaveLogger.logger().pushPrintln("[Intave] Failed to correctly emulate data structure of block type " + type + " (requested state " + blockState + ")");
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
      BiConsumer<Material, Map<Integer, Object>> registerApply,
      BiConsumer<Material, Map<Integer, BlockState>> stateApply
    ) {
      CraftBlockData blockData = CraftBlockData.newData(type, null);
      Block block = blockData.getState().getBlock();
      Map<Object, Integer> index = new HashMap<>();
      Map<Integer, Object> register = new HashMap<>();
      Map<Integer, BlockState> states = new HashMap<>();
      int id = 0;
      for (IBlockData nativeState : block.getStates().a()) {
        index.put(nativeState, id);
        register.put(id, nativeState);
//        states.put(id, BlockState.builder().build());
        id++;
      }
      indexApply.accept(type, index);
      registerApply.accept(type, register);
      stateApply.accept(type, states);
    }
  }
}

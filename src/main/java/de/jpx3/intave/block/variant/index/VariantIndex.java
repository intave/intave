package de.jpx3.intave.block.variant.index;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class VariantIndex {
  private static final Indexer INDEXER;

  static {
    String indexerClassName;
    if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      if (MinecraftVersions.VER1_14_0.atOrAbove()) {
        indexerClassName = "de.jpx3.intave.block.variant.index.ModernIndexer";
      } else {
        indexerClassName = "de.jpx3.intave.block.variant.index.AquaticIndexer";
      }
    } else {
      indexerClassName = "de.jpx3.intave.block.variant.index.LegacyIndexer";
    }
    Indexer indexer;
    try {
      Class<Indexer> indexerClass = PatchyLoadingInjector.loadUnloadedClassPatched(
        IntavePlugin.class.getClassLoader(), indexerClassName
      );
      if (indexerClass == null) {
        throw new IllegalStateException("Failed to load indexer class " + indexerClassName);
      }
      indexer = indexerClass.newInstance();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
    INDEXER = indexer;
  }

  public static void indexApplyWithReverse(
    Material type,
    BiConsumer<? super Material, ? super Map<Object, Integer>> indexApply,
    BiConsumer<? super Material, ? super Map<Integer, Object>> registerApply
  ) {
    Map<Object, Integer> index = index(type);
    // reverse map
    indexApply.accept(type, index);
    Map<Integer, Object> reversed = reversed(index);
    if (!reversed.containsKey(0)) {
      if (IntaveControl.DEBUG_VARIANT_COMPILATION) {
        System.out.println("[variant/debug] Block " + type + " has no zero state, using first state");
      }
      reversed.put(0, index.keySet().iterator().next());
    }
    registerApply.accept(type, reversed);
  }

  private static Map<Object, Integer> index(Material type) {
    return INDEXER.index(type);
  }

  private static <K, V> Map<V, K> reversed(Map<K, V> map) {
    Map<V, K> reversed = new HashMap<>();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      reversed.put(entry.getValue(), entry.getKey());
    }
    return reversed;
  }
}

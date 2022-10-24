package de.jpx3.intave.block.variant.index;

import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.BlockStateList;
import org.bukkit.Material;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

class LegacyIndexer implements Indexer {
  private static Method getStateListMethod;

  @PatchyAutoTranslation
  public Map<Object, Integer> index(
    Material type
  ) {
    int id = type.getId();
    net.minecraft.server.v1_8_R3.Block block = net.minecraft.server.v1_8_R3.Block.getById(id);
    Map<Object, Integer> index = new HashMap<>();
    try {
      if (getStateListMethod == null) {
        getStateListMethod = Lookup.serverClass("Block").getDeclaredMethod("getStateList");
        getStateListMethod.setAccessible(true);
      }
      BlockStateList blockStateList = (BlockStateList) getStateListMethod.invoke(block);
      for (net.minecraft.server.v1_8_R3.IBlockData blockData : blockStateList.a()) {
        index.put(blockData, block.toLegacyData(blockData));
      }
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return index;
  }
}

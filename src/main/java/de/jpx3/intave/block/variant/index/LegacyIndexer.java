package de.jpx3.intave.block.variant.index;

import de.jpx3.intave.IntaveControl;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_8_R3.BlockStateList;
import net.minecraft.server.v1_8_R3.IBlockData;
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
      boolean hasZeroState = false;
      IBlockData firstState = null;
      for (IBlockData blockData : blockStateList.a()) {
        int value = block.toLegacyData(blockData);
        index.put(blockData, value);
//        if (firstState == null) {
//          firstState = blockData;
//        }
//        if (value == 0) {
//          hasZeroState = true;
//        }
      }
//      if (!hasZeroState) {
//        if (IntaveControl.DEBUG_VARIANT_COMPILATION) {
//          System.out.println("[variant/debug] Block " + type + " has no zero state, using first state");
//        }
//        index.put(firstState, 0);
//      }
    } catch (Exception exception) {
      exception.printStackTrace();
    }
    return index;
  }
}

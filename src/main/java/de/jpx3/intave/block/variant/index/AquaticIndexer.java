package de.jpx3.intave.block.variant.index;

import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_13_R2.Block;
import net.minecraft.server.v1_13_R2.IBlockData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_13_R2.block.data.CraftBlockData;

import java.util.HashMap;
import java.util.Map;

class AquaticIndexer implements Indexer {
  @Override
  @PatchyAutoTranslation
  public Map<Object, Integer> index(Material type) {
    CraftBlockData blockData = CraftBlockData.newData(type, null);
    Block block = blockData.getState().getBlock();
    Map<Object, Integer> index = new HashMap<>();
    int id = 0;
    for (IBlockData nativeState : block.getStates().a()) {
      index.put(nativeState, id);
      id++;
    }
    return index;
  }
}

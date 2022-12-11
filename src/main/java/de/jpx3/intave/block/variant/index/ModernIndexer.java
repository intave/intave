package de.jpx3.intave.block.variant.index;

import de.jpx3.intave.klass.rewrite.PatchyAutoTranslation;
import net.minecraft.server.v1_14_R1.Block;
import net.minecraft.server.v1_14_R1.IBlockData;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_14_R1.block.data.CraftBlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.MIN_VALUE;

class ModernIndexer implements Indexer {
  @Override
  @PatchyAutoTranslation
  public Map<Object, Integer> index(Material type) {
    CraftBlockData blockData = CraftBlockData.newData(type, null);
    Block block = blockData.getState().getBlock();
    Map<Object, Integer> index = new HashMap<>();
    // issue with straight forward ids is the temptation to directly interpret them
    // as a legacy data value, which is not the case and will lead to issues.
    // To avoid this temptation, we use a random number as the id.

//    int id = 0;
    // zero is the first variant and the only variant that is allowed to be assumed

    Set<Integer> takenIds = new HashSet<>();
    int id = 0;
    for (IBlockData nativeState : block.getStates().a()) {
      index.put(nativeState, id);
      takenIds.add(id);
      do {
        id = ThreadLocalRandom.current().nextInt(1, MAX_VALUE);
      } while (takenIds.contains(id));
    }
    return index;
  }
}

package de.jpx3.intave.block.variant.index;

import org.bukkit.Material;

import java.util.Map;

class AquaticIndexer implements Indexer {
  @Override
  public Map<Object, Integer> index(Material type) {
    return PacketEventsBlockStateIndexer.index(type);
  }
}

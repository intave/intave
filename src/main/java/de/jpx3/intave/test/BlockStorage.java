package de.jpx3.intave.test;

import de.jpx3.intave.block.access.BlockAccess;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class BlockStorage {
  private final Block block;
  private final int x, y, z;
  private Material type;
  private Object data;

  public BlockStorage(Block block) {
    this.block = block;
    this.x = block.getX();
    this.y = block.getY();
    this.z = block.getZ();
  }

  public void store() {
    type = block.getType();
    data = BlockAccess.global().nativeVariantOf(block);
  }

  public void restore() {
    block.setType(type, false);
  }

  public static BlockStorage store(Block block) {
    BlockStorage storage = new BlockStorage(block);
    storage.store();
    return storage;
  }
}

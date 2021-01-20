package de.jpx3.intave.world.permission;

import de.jpx3.intave.patchy.annotate.PatchyAutoTranslation;
import de.jpx3.intave.patchy.annotate.PatchyTranslateParameters;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_8_R3.block.CraftBlock;

@PatchyAutoTranslation
public final class CustomCraftBlock extends CraftBlock {
  private final int typeId, data;

  @PatchyAutoTranslation
  @PatchyTranslateParameters
  public CustomCraftBlock(Object chunk, int x, int y, int z, int typeId, int data) {
    super((CraftChunk) chunk, x, y, z);
    this.typeId = typeId;
    this.data = data;
  }

  @Override
  public int getTypeId() {
    return typeId;
  }

  @Override
  public byte getData() {
    return (byte) data;
  }
}

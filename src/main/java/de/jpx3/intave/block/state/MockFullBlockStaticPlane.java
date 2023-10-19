package de.jpx3.intave.block.state;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public final class MockFullBlockStaticPlane implements ExtendedBlockStateCache {
  // 16 * 256 * 16
  private final BitSet bitSet = new BitSet(16 * 256 * 16);

  private boolean isStone(int posX, int posY, int posZ) {
    if (posY < 0 || posY >= 256 || posX < 0 || posX >= 16 || posZ < 0 || posZ >= 16) {
      return false;
    }
    return bitSet.get(posX + (posZ << 4) + (posY << 8));
  }

  private void setStone(int posX, int posY, int posZ) {
    if (posY < 0 || posY >= 256 || posX < 0 || posX >= 16 || posZ < 0 || posZ >= 16) {
      throw new IllegalArgumentException("Invalid position: " + posX + ", " + posY + ", " + posZ);
    }
    bitSet.set(posX + (posZ << 4) + (posY << 8));
  }

  public void horizontalFill(int posY) {
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        setStone(x, posY, z);
      }
    }
  }

  @Override
  public @NotNull BlockShape outlineShapeAt(int posX, int posY, int posZ) {
    return isStone(posX, posY, posZ) ? BlockShapes.cubeAt(posX, posY, posZ) : BlockShapes.emptyShape();
  }

  @Override
  public @NotNull BlockShape collisionShapeAt(int posX, int posY, int posZ) {
    return isStone(posX, posY, posZ) ? BlockShapes.cubeAt(posX, posY, posZ) : BlockShapes.emptyShape();
  }

  @Override
  public @NotNull Material typeAt(int posX, int posY, int posZ) {
    return isStone(posX, posY, posZ) ? Material.STONE : Material.AIR;
  }

  @Override
  public int variantIndexAt(int posX, int posY, int posZ) {
    return 0;
  }

  @Override
  public void invalidateAll() {

  }

  @Override
  public void invalidateCache() {

  }

  @Override
  public void invalidateCacheAt0(int posX, int posY, int posZ) {

  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    return false;
  }

  @Override
  public BlockState overrideOf(int posX, int posY, int posZ) {
    return null;
  }

  @Override
  public void lockOverride(int posX, int posY, int posZ) {

  }

  @Override
  public void unlockOverride(int posX, int posY, int posZ) {

  }

  @Override
  public void invalidateOverride(int posX, int posY, int posZ) {

  }

  @Override
  public int numOfIndexedReplacements() {
    return 0;
  }

  @Override
  public int numOfLocatedReplacements() {
    return 0;
  }

  @Override
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {

  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {

  }
}

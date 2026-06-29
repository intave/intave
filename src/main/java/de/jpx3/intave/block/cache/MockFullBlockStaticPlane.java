package de.jpx3.intave.block.cache;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public final class MockFullBlockStaticPlane implements BlockCache {
  // 16 * 256 * 16
  private final Long2BooleanOpenHashMap blockMap = new Long2BooleanOpenHashMap();

  boolean isStone(int posX, int posY, int posZ) {
    if (posY < -256 || posY >= 256 || posX < -256 || posX >= 256 || posZ < -256 || posZ >= 256) {
      return false;
    }
    return blockMap.get(getKey(posX, posY, posZ));
  }

  private void setStone(int posX, int posY, int posZ) {
    if (posY < -256 || posY >= 256 || posX < -256 || posX >= 256 || posZ < -256 || posZ >= 256) {
      throw new IllegalArgumentException("Invalid position: " + posX + ", " + posY + ", " + posZ);
    }
    blockMap.put(getKey(posX, posY, posZ), true);
  }

  private long getKey(int posX, int posY, int posZ) {
    return ((long) posX & 0xFFFF) | (((long) posY & 0xFFFF) << 16) | (((long) posZ & 0xFFFF) << 32);
  }

  public void horizontalFill(int posY) {
    int limit = 256;
    for (int x = -limit; x < limit; x++) {
      for (int z = -limit; z < limit; z++) {
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
  public boolean isClientSpeculatingAt(int posX, int posY, int posZ) {
    return false;
  }

  @Override
  public void setClientSpeculationValue(World world, int posX, int posY, int posZ, Material type, int variant, int seq) {

  }

  @Override
  public void undoClientSpeculation(World world, int posX, int posY, int posZ) {

  }

  @Override
  public void moveClientSpeculationsToOverride(World world, int seq) {

  }

  @Override
  public void invalidateAll() {

  }

  @Override
  public void invalidateCache() {

  }

  @Override
  public void invalidateCacheAt(int posX, int posY, int posZ) {

  }

  @Override
  public boolean currentlyInOverride(int posX, int posY, int posZ) {
    return false;
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

  @Override
  public boolean hasOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    return false;
  }

  public static MockFullBlockStaticPlane createWithHorizontalPlaneAt(int posY) {
    MockFullBlockStaticPlane plane = new MockFullBlockStaticPlane();
    plane.horizontalFill(posY);
    return plane;
  }
}

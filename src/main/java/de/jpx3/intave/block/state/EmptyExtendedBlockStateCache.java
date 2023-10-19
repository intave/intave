package de.jpx3.intave.block.state;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.BlockShapes;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

final class EmptyExtendedBlockStateCache implements ExtendedBlockStateCache {
  @Override
  public @NotNull BlockShape outlineShapeAt(int posX, int posY, int posZ) {
    return BlockShapes.emptyShape();
  }

  @Override
  public @NotNull BlockShape collisionShapeAt(int posX, int posY, int posZ) {
    return BlockShapes.emptyShape();
  }

  @Override
  public @NotNull Material typeAt(int posX, int posY, int posZ) {
    return Material.AIR;
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
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
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
}

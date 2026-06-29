package de.jpx3.intave.block.cache;

import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

final class EmptyExtendedBlockStateCache implements BlockCache {
  @Override
  public @NotNull BlockState stateAt(int posX, int posY, int posZ) {
    return BlockState.empty();
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
  public void override(World world, int posX, int posY, int posZ, Material type, int variant) {
  }

  @Override
  public void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
  }

  @Override
  public boolean hasOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    return false;
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
}

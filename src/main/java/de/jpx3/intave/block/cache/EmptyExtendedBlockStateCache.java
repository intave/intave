/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.block.cache;

import de.jpx3.intave.share.BlockCacheEntry;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

final class EmptyExtendedBlockStateCache implements BlockCache {
  @Override
  public BlockCacheEntry peekStateAt(int posX, int posY, int posZ) {
    return BlockCacheEntry.empty();
  }

  @Override
  public @NotNull BlockCacheEntry stateAt(int posX, int posY, int posZ) {
    return BlockCacheEntry.empty();
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

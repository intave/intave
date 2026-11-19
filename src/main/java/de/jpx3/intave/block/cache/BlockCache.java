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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.share.BlockCacheEntry;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.PositionedBlockState;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public interface BlockCache {
  /**
   * Returns an already-resolved state without loading world data or mutating this cache.
   * A {@code null} result means that the position has not been resolved.
   */
  default @Nullable BlockCacheEntry peekStateAt(int posX, int posY, int posZ) {
    return null;
  }

  default @NotNull BlockCacheEntry stateAt(int posX, int posY, int posZ) {
    throw new UnsupportedOperationException("stateAt(int, int, int) is not implemented");
  }

  /**
   * Resolve-if-not-cached and retrieve the outline shape of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks bounding boxes
   */
  default @NotNull BlockShape outlineShapeAt(int posX, int posY, int posZ) {
    return stateAt(posX, posY, posZ).outlineShape();
  }

  default @NotNull BlockShape outlineShapeAt(BlockPosition position) {
    return outlineShapeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  /**
   * Resolve-if-not-cached and retrieve the collision shape of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks bounding boxes
   */
  default @NotNull BlockShape collisionShapeAt(int posX, int posY, int posZ) {
    return stateAt(posX, posY, posZ).collisionShape();
  }

  default @NotNull BlockShape collisionShapeAt(BlockPosition position) {
    return collisionShapeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  /**
   * Resolve-if-not-cached and retrieve the type of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks type
   */
  default @NotNull Material typeAt(int posX, int posY, int posZ) {
    return stateAt(posX, posY, posZ).type();
  }

  default @NotNull Material typeAt(BlockPosition position) {
    return typeAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  /**
   * Resolve-if-not-cached and retrieve the variant index of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks variant index
   */
  default int variantIndexAt(int posX, int posY, int posZ) {
    return stateAt(posX, posY, posZ).variantIndex();
  }

  default int variantIndexAt(BlockPosition position) {
    return variantIndexAt(position.getBlockX(), position.getBlockY(), position.getBlockZ());
  }

  boolean isClientSpeculatingAt(int posX, int posY, int posZ);

  void setClientSpeculationValue(World world, int posX, int posY, int posZ, Material type, int variant, int sequenceNumber);

  default boolean updateClientSpeculationValue(World world, int posX, int posY, int posZ, Material type, int variant) {
    return false;
  }

  void undoClientSpeculation(World world, int posX, int posY, int posZ);

  void moveClientSpeculationsToOverride(World world, int requiredSequenceNumber);

  /**
   * Retrieves if this position is currently being overridden
   *
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   * @return whether the block is currently in override
   */
  boolean currentlyInOverride(int posX, int posY, int posZ);

 /**
  * Locks the specified location from being overridden
  * @param posX
  * @param posY
  * @param posZ
  */
  void lockOverride(int posX, int posY, int posZ);

 /**
  * Unlocks the specified location from being overridden
  * @param posX
  * @param posY
  * @param posZ
  */
  void unlockOverride(int posX, int posY, int posZ);

  /**
   * Remove a blocks override
   *
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  void invalidateOverride(int posX, int posY, int posZ);

  // debug
  int numOfIndexedReplacements();
  int numOfLocatedReplacements();

  default void override(World world, int posX, int posY, int posZ, Material type, int variant) {
    override(world, posX, posY, posZ, type, variant, "unknown");
  }

  /**
   * Override a block at a specific position with a custom type and variant.
   *
   * @param world   the world
   * @param posX    the x coordinate of the selected block
   * @param posY    the y coordinate of the selected block
   * @param posZ    the z coordinate of the selected block
   * @param type    the selected type
   * @param variant the selected variant
   */
  default void override(World world, int posX, int posY, int posZ, Material type, int variant, String reason) {

  }

  /**
   * Override a block at a specific position with a custom type and variant.
   *
   * @param world          the world
   * @param blockState     the selected block state
   * @param reason         the reason for the override
   */
  default void override(World world, PositionedBlockState blockState, String reason) {
    override(world, blockState.position().getBlockX(), blockState.position().getBlockY(), blockState.position().getBlockZ(), blockState.state().type(), blockState.state().variantIndex(), reason);
  }

  /**
   * Remove all overrides in specified chunk boundaries
   *
   * @param chunkXMinPos the min chunk x boundary
   * @param chunkXMaxPos the max chunk x boundary
   * @param chunkZMinPos the min chunk z boundary
   * @param chunkZMaxPos the max chunk z boundary
   */
  void invalidateOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos);

  /**
   * Check if there are any overrides in the specified chunk boundaries
   *
   * @param chunkXMinPos the min chunk x boundary
   * @param chunkXMaxPos the max chunk x boundary
   * @param chunkZMinPos the min chunk z boundary
   * @param chunkZMaxPos the max chunk z boundary
   * @return whether there are any overrides in the specified chunk boundaries
   */
  boolean hasOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos);

  /**
   * Invalidate all caches
   */
  void invalidateAll();

  /**
   * Invalidate resolver caches
   */
  void invalidateCache();

  /**
   * Invalidate all blocks adjacent to the specified position
   *
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  default void invalidateCacheAround(int posX, int posY, int posZ) {
    invalidateCacheAt(posX + 1, posY, posZ);
    invalidateCacheAt(posX - 1, posY, posZ);
    invalidateCacheAt(posX, posY, posZ + 1);
    invalidateCacheAt(posX, posY, posZ - 1);
    invalidateCacheAt(posX, posY + 1, posZ);
    invalidateCacheAt(posX, posY - 1, posZ);
    invalidateCacheAt(posX, posY, posZ);
  }

  /**
   * Invalidate a cache entry
   *
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  void invalidateCacheAt(int posX, int posY, int posZ);
}

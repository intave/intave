package de.jpx3.intave.block.cache;

import de.jpx3.intave.block.shape.BlockShape;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public interface BlockCache {
  default @NotNull BlockState stateAt(int posX, int posY, int posZ) {
    throw new UnsupportedOperationException();
  }

  /**
   * Resolve-if-not-cached and retrieve the outline shape of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks bounding boxes
   */
  @NotNull BlockShape outlineShapeAt(int posX, int posY, int posZ);

  /**
   * Resolve-if-not-cached and retrieve the collision shape of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks bounding boxes
   */
  @NotNull BlockShape collisionShapeAt(int posX, int posY, int posZ);

  /**
   * Resolve-if-not-cached and retrieve the type of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks type
   */
  @NotNull Material typeAt(int posX, int posY, int posZ);

  /**
   * Resolve-if-not-cached and retrieve the variant index of the specified block.
   *
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks variant index
   */
  int variantIndexAt(int posX, int posY, int posZ);

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
   * Retrieve the blocks override
   *
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   * @return the override's blockshape
   */
  @Deprecated
  BlockState overrideOf(int posX, int posY, int posZ);

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

  };

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

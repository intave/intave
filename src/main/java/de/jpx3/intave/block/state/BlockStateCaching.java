package de.jpx3.intave.block.state;

/**
 * The {@link BlockStateCaching} extends the functionality of the
 * {@link BlockStateLookup} by adding methods related to cache invalidation.
 *
 * @see BlockStateLookup
 * @see BlockStateOverrides
 * @see BlockStateAccess
 */

public interface BlockStateCaching {
  /**
   * Invalidate all caches
   */
  void invalidateAll();

  /**
   * Invalidate resolver caches
   */
  void invalidateCache();

  /**
   * Invalidate all blocks next to a specified position
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  default void invalidateCacheAt(int posX, int posY, int posZ) {
    invalidateCacheAt0(posX + 1, posY, posZ);
    invalidateCacheAt0(posX - 1, posY, posZ);
    invalidateCacheAt0(posX, posY, posZ + 1);
    invalidateCacheAt0(posX, posY, posZ - 1);
    invalidateCacheAt0(posX, posY + 1, posZ);
    invalidateCacheAt0(posX, posY - 1, posZ);
    invalidateCacheAt0(posX, posY, posZ);
  }

  /**
   * Invalidate a specific block
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  void invalidateCacheAt0(int posX, int posY, int posZ);
}

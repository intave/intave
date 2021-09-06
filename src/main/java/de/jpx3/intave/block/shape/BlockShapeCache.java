package de.jpx3.intave.block.shape;

/**
 * The {@link BlockShapeCache} extends the functionality of the
 * {@link BlockShapeLookup} by adding methods related to cache invalidation.
 *
 * @see BlockShapeLookup
 * @see BlockShapeOverrides
 * @see BlockShapeAccess
 */

public interface BlockShapeCache extends BlockShapeLookup {
  /**
   * Invalidate all caches
   */
  void identityInvalidate();

  /**
   * Invalidate resolver caches
   */
  void invalidate();

  /**
   * Invalidate all blocks next to a specified position
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  default void invalidate(int posX, int posY, int posZ) {
    invalidate0(posX + 1, posY, posZ);
    invalidate0(posX - 1, posY, posZ);
    invalidate0(posX, posY, posZ + 1);
    invalidate0(posX, posY, posZ - 1);
    invalidate0(posX, posY + 1, posZ);
    invalidate0(posX, posY - 1, posZ);
    invalidate0(posX, posY, posZ);
  }

  /**
   * Invalidate a specific block
   * @param posX the x coordinate of the selected block
   * @param posY the y coordinate of the selected block
   * @param posZ the z coordinate of the selected block
   */
  void invalidate0(int posX, int posY, int posZ);
}

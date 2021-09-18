package de.jpx3.intave.block.state;

import de.jpx3.intave.block.shape.BlockShape;
import de.jpx3.intave.block.shape.ShapeResolver;
import de.jpx3.intave.user.User;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * A BlockShapeAccess serves as an auto-resolving cache for block types, block bounding boxes and block variants.
 *
 * @see User
 * @see BlockStateCache
 * @see BlockStateOverrides
 * @see BlockStateAccess
 * @see MultiChunkKeyBlockStateAccess
 * @see EmptyBlockStateAccess
 * @see ShapeResolver
 */
public interface BlockStateLookup {
  /**
   * Resolve-if-not-cached and retrieve the bounding boxes of the specified block.
   * @param chunkX the chunk x coordinate
   * @param chunkZ the chunk z coordinate
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks bounding boxes
   */
  @NotNull BlockShape resolveShape(int posX, int posY, int posZ);

  /**
   * Resolve-if-not-cached and retrieve the type of the specified block.
   * @param chunkX the chunk x coordinate
   * @param chunkZ the chunk z coordinate
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks type
   */
  @NotNull Material resolveType(int chunkX, int chunkZ, int posX, int posY, int posZ);

  /**
   * Resolve-if-not-cached and retrieve the variant index of the specified block.
   * @param chunkX the chunk x coordinate
   * @param chunkZ the chunk z coordinate
   * @param posX the blocks x coordinate
   * @param posY the blocks y coordinate
   * @param posZ the blocks z coordinate
   * @return the blocks variant index
   */
  int resolveVariantIndex(int chunkX, int chunkZ, int posX, int posY, int posZ);
}

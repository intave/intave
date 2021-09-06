package de.jpx3.intave.block.shape;

import de.jpx3.intave.user.User;

/**
 * A block shape access  merges
 * {@link BlockShapeCache} featuring methods for type caching
 * {@link BlockShapeOverrides}, featuring methods for type override and
 * {@link BlockShapeLookup} for basic lookup access.
 *
 * @see User
 * @see BlockShape
 * @see BlockShapeLookup
 * @see BlockShapeOverrides
 * @see BlockShapeCache
 * @see MultiChunkKeyBlockShapeAccess
 */
public interface BlockShapeAccess extends BlockShapeOverrides, BlockShapeCache, BlockShapeLookup {
}

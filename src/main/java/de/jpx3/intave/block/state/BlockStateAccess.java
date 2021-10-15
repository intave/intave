package de.jpx3.intave.block.state;

import de.jpx3.intave.user.User;

/**
 * A block state access  merges
 * {@link BlockStateCaching} featuring methods for type caching
 * {@link BlockStateOverrides}, featuring methods for type override and
 * {@link BlockStateLookup} for basic lookup access.
 *
 * @see User
 * @see BlockState
 * @see BlockStateLookup
 * @see BlockStateOverrides
 * @see BlockStateCaching
 * @see MultiChunkKeyBlockStateAccess
 */
public interface BlockStateAccess extends BlockStateLookup, BlockStateCaching, BlockStateOverrides {
}
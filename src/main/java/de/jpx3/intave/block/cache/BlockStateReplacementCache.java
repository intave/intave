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

import com.google.common.collect.Sets;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.Position;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class BlockStateReplacementCache {
  private final Map<Position, BlockState> located = new ConcurrentHashMap<>(96);
  private final Map<Position, Long> locked = new ConcurrentHashMap<>(96);
  private final Long2ReferenceMap<BlockState> indexed = Long2ReferenceMaps.synchronize(new Long2ReferenceOpenHashMap<>(96));

	private final Function<? super Position, ? extends Long> keyer;

  BlockStateReplacementCache(Function<? super Position, ? extends Long> keyer) {
	  this.keyer = keyer;
  }

  public BlockState byKey(long index) {
    return indexed.get(index);
  }

  public void insert(Position position, BlockState blockState) {
    located.put(position, blockState);
    indexed.put(keyer.apply(position).longValue(), blockState);
  }

  public void lock(Position position) {
    locked.put(position, System.currentTimeMillis());
  }

  public boolean unlock(Position position) {
    return locked.remove(position) != null;
  }

  private boolean isLocked(Position position) {
    return locked.containsKey(position) && System.currentTimeMillis() - locked.get(position) < 5000L;
  }

  public void remove(long key) {
    indexed.remove(key);
  }

  public boolean contains(long key) {
    return indexed.containsKey(key);
  }

  public void internalRefresh() {
    for (Position location : located.keySet()) {
      if (isLocked(location)) {
        continue;
      }
      BlockState blockState = located.get(location);
      if (blockState == null || blockState.expired()) {
        located.remove(location);
        BlockState old = indexed.remove(keyer.apply(location));
        if (old != null) {
        }
        locked.remove(location);
      }
    }
    // remove locked entries that are older than 10 seconds
    locked.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 10000L);
  }

  public void chunkReset(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Position location : located.keySet()) {
      if (isLocked(location)) {
        continue;
      }
      if (location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
        location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos) {
        long key = keyer.apply(location);
        located.remove(location);
        BlockState old = indexed.remove(key);
        if (old != null) {
        }
        locked.remove(location);
      }
    }
  }

  public boolean hasOverridesInBounds(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Position location : located.keySet()) {
      if (location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
        location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos) {
        return true;
      }
    }
    return false;
  }

  public void clear() {
    locked.clear();
    located.clear();
    indexed.clear();
  }

  public Map<Position, BlockState> located() {
    return located;
  }

  public Map<Long, BlockState> indexed() {
    return indexed;
  }

  public Set<Position> locations() {
    return located.keySet();
  }
}

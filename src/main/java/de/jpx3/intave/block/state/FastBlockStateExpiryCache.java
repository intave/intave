package de.jpx3.intave.block.state;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.jpx3.intave.shade.Position;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;

final class FastBlockStateExpiryCache {
  private final Map<Location, BlockState> located = Maps.newConcurrentMap();
  private final Map<Long, BlockState> indexed = Maps.newConcurrentMap();
  private final Set<Location> locations = Sets.newConcurrentHashSet();

  public BlockState byKey(long index) {
    return indexed.get(index);
  }

  public void insert(Location position, BlockState blockState) {
    located.put(position, blockState);
    locations.add(position);
    indexed.put(bigKey(position), blockState);
  }

  public void remove(long key) {
    indexed.remove(key);
  }

  public boolean replaced(long key) {
    return indexed.containsKey(key);
  }

  public void internalRefresh() {
    for (Location location : locations) {
      BlockState blockState = located.get(location);
      if (blockState == null || blockState.expired()) {
        locations.remove(location);
        located.remove(location);
        indexed.remove(bigKey(location));
      }
    }
  }

  public void chunkReset(int chunkXMinPos, int chunkXMaxPos, int chunkZMinPos, int chunkZMaxPos) {
    for (Location location : located.keySet()) {
      if (location.getX() >= chunkXMinPos && location.getX() < chunkXMaxPos &&
        location.getZ() >= chunkZMinPos && location.getZ() < chunkZMaxPos) {
        long key = bigKey(location);
        located.remove(location);
        locations.remove(location);
        indexed.remove(key);
      }
    }
  }

  public void clear() {
    located.clear();
    indexed.clear();
    locations.clear();
  }

  public Map<Location, BlockState> located() {
    return located;
  }

  public Map<Long, BlockState> indexed() {
    return indexed;
  }

  public Set<Location> locations() {
    return locations;
  }

  private long bigKey(Position position) {
    return bigKey(position.blockX(), position.blockY(), position.blockZ());
  }

  private long bigKey(Location location) {
    return bigKey(location.getBlockX(), location.getBlockY(), location.getBlockZ());
  }

  private long bigKey(int posX, int posY, int posZ) {
    return (posX & 0x3fffffL) << 42 | (posY & 0xfffffL) | (posZ & 0x3fffffL) << 20;
  }
}

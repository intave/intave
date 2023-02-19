package de.jpx3.intave.world.border;

import de.jpx3.intave.annotate.refactoring.MyNameIsTooAbstract;
import de.jpx3.intave.cleanup.GarbageCollector;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@MyNameIsTooAbstract
final class CachedForwardingWorldBorderAccess implements WorldBorderAccess {
  private static final long CACHE_EXPIRY = TimeUnit.MILLISECONDS.toMillis(50);
  private final WorldBorderAccess forward;
  private final Map<World, WorldBorderAccessCache<Double>> sizeCache = GarbageCollector.watch(new ConcurrentHashMap<>());
  private final Map<World, WorldBorderAccessCache<Location>> locationCache = GarbageCollector.watch(new ConcurrentHashMap<>());

  public CachedForwardingWorldBorderAccess(WorldBorderAccess forward) {
    this.forward = forward;
  }

  @Override
  public double sizeOf(World world) {
    WorldBorderAccessCache<Double> sizeCache = this.sizeCache.get(world);
    if (sizeCache == null) {
      sizeCache = new WorldBorderAccessCache<>(forward.sizeOf(world));
      this.sizeCache.put(world, sizeCache);
    } else if (sizeCache.expired()) {
      sizeCache.refresh(forward.sizeOf(world));
    }
    return sizeCache.value();
  }

  @Override
  public Location centerOf(World world) {
    WorldBorderAccessCache<Location> locationCache = this.locationCache.get(world);
    if (locationCache == null) {
      locationCache = new WorldBorderAccessCache<>(forward.centerOf(world));
      this.locationCache.put(world, locationCache);
    } else if (locationCache.expired()) {
      locationCache.refresh(forward.centerOf(world));
    }
    return locationCache.value();
  }

  public static class WorldBorderAccessCache<T> {
    private T value;
    private long time;

    public WorldBorderAccessCache(T value) {
      this.value = value;
      this.time = System.currentTimeMillis();
    }

    public void refresh(T newValue) {
      value = newValue;
      time = System.currentTimeMillis();
    }

    public T value() {
      return value;
    }

    public boolean expired() {
      return System.currentTimeMillis() - time > CACHE_EXPIRY;
    }
  }
}

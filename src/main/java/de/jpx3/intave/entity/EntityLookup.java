package de.jpx3.intave.entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public final class EntityLookup {
  private static final Cache<EntityCacheKey, Entity> entityAccessCache =
    CacheBuilder.newBuilder()
      .expireAfterAccess(16, TimeUnit.SECONDS).weakValues()
      .concurrencyLevel(8).build();

  public static void setup() {

  }

  public static @Nullable Entity findEntity(World world, int identifier) {
    EntityCacheKey cacheKey = new EntityCacheKey(world.getUID().hashCode(), identifier);
    Entity entity = entityAccessCache.getIfPresent(cacheKey);
    if (entity != null) {
      return entity;
    }
    entity = entityById(world, identifier);
    if (entity != null) {
      entityAccessCache.put(cacheKey, entity);
    }
    return entity;
  }

  private static @Nullable Entity entityById(World world, int identifier) {
    for (Entity entity : world.getEntities()) {
      if (entity.getEntityId() == identifier) {
        return entity;
      }
    }
    return null;
  }

  private static final class EntityCacheKey {
    private final int worldId;
    private final int entityId;

    private EntityCacheKey(int worldId, int entityId) {
      this.worldId = worldId;
      this.entityId = entityId;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof EntityCacheKey)) {
        return false;
      }
      EntityCacheKey that = (EntityCacheKey) other;
      return worldId == that.worldId && entityId == that.entityId;
    }

    @Override
    public int hashCode() {
      return Objects.hash(worldId, entityId);
    }
  }
}

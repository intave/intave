package de.jpx3.intave.entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.locate.MethodSearchBySignature;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.TimeUnit;

import static de.jpx3.intave.reflect.access.ReflectiveHandleAccess.handleOf;

@SuppressWarnings("UnstableApiUsage")
public final class EntityLookup {
  private static final MethodHandle entityByIdAccessor = MethodSearchBySignature
    .search(Lookup.serverClass("World"), new Class[]{Integer.TYPE}, Lookup.serverClass("Entity"))
    .findAnyOrThrow();
  private static final MethodHandle bukkitEntityFromEntityAccessor = MethodSearchBySignature
    .search(Lookup.serverClass("Entity"), new Class[0], Lookup.craftBukkitClass("entity.CraftEntity"))
    .findAnyOrThrow();
  private static final Cache<Integer, Entity> entityAccessCache =
    CacheBuilder.newBuilder()
      .expireAfterAccess(16, TimeUnit.SECONDS).weakValues()
      .concurrencyLevel(8).build();

  public static @Nullable Entity findEntity(World world, int identifier) {
    Entity entity = entityAccessCache.getIfPresent(identifier);
    if (entity != null) return entity;
    entity = entityById(world, identifier);
    if (entity != null) {
      entityAccessCache.put(identifier, entity);
    }
    return entity;
  }

  private static @Nullable Entity entityById(World world, int identifier) {
    try {
      Object serverEntity = entityByIdAccessor.invoke(handleOf(world), identifier);
      if (serverEntity == null) {
        return null;
      }
      return (Entity) bukkitEntityFromEntityAccessor.invoke(serverEntity);
    } catch (Throwable exception) {
      exception.printStackTrace();
      return null;
    }
  }
}

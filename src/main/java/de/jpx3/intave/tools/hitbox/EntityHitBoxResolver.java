package de.jpx3.intave.tools.hitbox;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.ReflectiveAccess;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;

public final class EntityHitBoxResolver {
  private final static boolean ENTITY_SIZE_CLASS = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.VILLAGE_UPDATE);
  private static Field entitySizeField;

  static {
    try {
      if (ENTITY_SIZE_CLASS) {
        setupEntitySizeField();
      }
    } catch (NoSuchFieldException e) {
      throw new IntaveInternalException(e);
    }
  }

  private static void setupEntitySizeField() throws NoSuchFieldException {
    Field entitySize = ReflectiveAccess.NMS_ENTITY_CLASS.getDeclaredField("size");
    if (!entitySize.isAccessible()) {
      entitySize.setAccessible(true);
    }
    entitySizeField = entitySize;
  }

  public static HitBoxBoundaries resolveHitBoxOf(Entity entity) {
    return resolveHitBoxOf(ReflectiveAccess.handleResolver().resolveEntityHandleOf(entity));
  }

  public static HitBoxBoundaries resolveHitBoxOf(Object entity) {
    float width;
    float height;
    if (ENTITY_SIZE_CLASS) {
      Object entitySize = resolveEntitySizeOf(entity);
      Class<?> entitySizeClass = entitySize.getClass();
      width = ReflectiveAccess.invokeField(entitySizeClass, "width", entitySize);
      height = ReflectiveAccess.invokeField(entitySizeClass, "height", entitySize);
    } else {
      Class<?> entityClass = ReflectiveAccess.NMS_ENTITY_CLASS;
      width = ReflectiveAccess.invokeField(entityClass, "width", entity);
      height = ReflectiveAccess.invokeField(entityClass, "length", entity);
    }
    return HitBoxBoundaries.from(width, height);
  }

  private static Object resolveEntitySizeOf(Object entity) {
    try {
      return entitySizeField.get(entity);
    } catch (IllegalAccessException e) {
      throw new IntaveInternalException(e);
    }
  }
}
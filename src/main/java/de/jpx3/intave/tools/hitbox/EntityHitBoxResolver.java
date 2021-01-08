package de.jpx3.intave.tools.hitbox;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.Reflection;
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
    Field entitySize = Reflection.NMS_ENTITY_CLASS.getDeclaredField("size");
    if (!entitySize.isAccessible()) {
      entitySize.setAccessible(true);
    }
    entitySizeField = entitySize;
  }

  public static HitBoxBoundaries resolveHitBoxOf(Entity entity) {
    return resolveHitBoxOf(Reflection.resolveEntityNMSHandle(entity));
  }

  public static HitBoxBoundaries resolveHitBoxOf(Object entity) {
    float width;
    float height;
    if (ENTITY_SIZE_CLASS) {
      Object entitySize = resolveEntitySizeOf(entity);
      Class<?> entitySizeClass = entitySize.getClass();
      width = Reflection.invokeField(entitySizeClass, "width", entitySize);
      height = Reflection.invokeField(entitySizeClass, "height", entitySize);
    } else {
      Class<?> entityClass = Reflection.NMS_ENTITY_CLASS;
      width = Reflection.invokeField(entityClass, "width", entity);
      height = Reflection.invokeField(entityClass, "length", entity);
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
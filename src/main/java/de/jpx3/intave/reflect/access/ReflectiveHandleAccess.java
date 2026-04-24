package de.jpx3.intave.reflect.access;

import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public final class ReflectiveHandleAccess {
  private static final Map<Class<?>, Method> HANDLE_METHODS = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Field> CONNECTION_FIELDS = new ConcurrentHashMap<>();

  public static Object handleOf(Entity entity) {
    return handleOf((Object) entity);
  }

  public static Object playerConnectionOf(Entity entity) {
    Object handle = handleOf(entity);
    Field connectionField = CONNECTION_FIELDS.computeIfAbsent(handle.getClass(), ReflectiveHandleAccess::findConnectionField);
    try {
      return connectionField.get(handle);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Unable to read player connection from " + handle.getClass().getName(), exception);
    }
  }

  public static Object handleOf(World world) {
    return handleOf((Object) world);
  }

  private static Object handleOf(Object craftObject) {
    Method getHandle = HANDLE_METHODS.computeIfAbsent(craftObject.getClass(), ReflectiveHandleAccess::findHandleMethod);
    try {
      return getHandle.invoke(craftObject);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IllegalStateException("Unable to read native handle from " + craftObject.getClass().getName(), exception);
    }
  }

  private static Method findHandleMethod(Class<?> type) {
    try {
      Method method = type.getMethod("getHandle");
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Unable to find getHandle() on " + type.getName(), exception);
    }
  }

  private static Field findConnectionField(Class<?> type) {
    Field field = findField(type, "playerConnection");
    if (field == null) {
      field = findField(type, "connection");
    }
    if (field == null) {
      throw new IllegalStateException("Unable to find player connection field on " + type.getName());
    }
    field.setAccessible(true);
    return field;
  }

  private static Field findField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        current = current.getSuperclass();
      }
    }
    return null;
  }
}

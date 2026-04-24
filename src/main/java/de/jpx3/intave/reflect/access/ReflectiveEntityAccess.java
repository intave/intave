package de.jpx3.intave.reflect.access;

import de.jpx3.intave.access.IntaveInternalException;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Deprecated
public final class ReflectiveEntityAccess {
  private static final Map<Class<?>, GroundAccess> GROUND_ACCESS = new ConcurrentHashMap<>();
  private static final Map<Class<?>, Method> SEND_METHODS = new ConcurrentHashMap<>();

  public static void setOnGround(Player player, boolean onGround) {
    Object entity = ReflectiveHandleAccess.handleOf(player);
    groundAccess(entity).set(entity, onGround);
  }

  public static boolean onGround(Player player) {
    Object entity = ReflectiveHandleAccess.handleOf(player);
    return groundAccess(entity).get(entity);
  }

  public static void addToSendQueue(Player player, Object packet) {
    Object connection = ReflectiveHandleAccess.playerConnectionOf(player);
    Method sendMethod = SEND_METHODS.computeIfAbsent(connection.getClass(), type -> findSendMethod(type, packet.getClass()));
    try {
      sendMethod.invoke(connection, packet);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private static GroundAccess groundAccess(Object entity) {
    return GROUND_ACCESS.computeIfAbsent(entity.getClass(), GroundAccess::find);
  }

  private static Method findSendMethod(Class<?> type, Class<?> packetType) {
    Method sendMethod = findMethod(type, "sendPacket", packetType);
    if (sendMethod == null) {
      sendMethod = findMethod(type, "send", packetType);
    }
    if (sendMethod == null) {
      throw new IllegalStateException("Unable to find packet send method on " + type.getName());
    }
    sendMethod.setAccessible(true);
    return sendMethod;
  }

  private static Method findMethod(Class<?> type, String name, Class<?> argumentType) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      for (Method method : current.getDeclaredMethods()) {
        if (method.getName().equals(name)
          && method.getParameterCount() == 1
          && method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
          return method;
        }
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static final class GroundAccess {
    private final Method getter;
    private final Method setter;
    private final Field field;

    private GroundAccess(Method getter, Method setter, Field field) {
      this.getter = getter;
      this.setter = setter;
      this.field = field;
    }

    private boolean get(Object entity) {
      try {
        if (getter != null) {
          return (boolean) getter.invoke(entity);
        }
        return (boolean) field.get(entity);
      } catch (IllegalAccessException | InvocationTargetException exception) {
        throw new IntaveInternalException(exception);
      }
    }

    private void set(Object entity, boolean onGround) {
      try {
        if (setter != null) {
          setter.invoke(entity, onGround);
        } else {
          field.set(entity, onGround);
        }
      } catch (IllegalAccessException | InvocationTargetException exception) {
        throw new IntaveInternalException(exception);
      }
    }

    private static GroundAccess find(Class<?> entityType) {
      Method getter = findNoArgBooleanMethod(entityType, "onGround");
      if (getter == null) {
        getter = findNoArgBooleanMethod(entityType, "isOnGround");
      }
      Method setter = findBooleanSetter(entityType, "setOnGround");
      if (getter != null && setter != null) {
        getter.setAccessible(true);
        setter.setAccessible(true);
        return new GroundAccess(getter, setter, null);
      }
      Field field = findBooleanField(entityType, "onGround");
      if (field == null) {
        throw new IllegalStateException("Unable to find onGround access on " + entityType.getName());
      }
      field.setAccessible(true);
      return new GroundAccess(null, null, field);
    }

    private static Method findNoArgBooleanMethod(Class<?> type, String name) {
      Class<?> current = type;
      while (current != null && current != Object.class) {
        try {
          Method method = current.getDeclaredMethod(name);
          if (method.getReturnType() == Boolean.TYPE) {
            return method;
          }
        } catch (NoSuchMethodException ignored) {
        }
        current = current.getSuperclass();
      }
      return null;
    }

    private static Method findBooleanSetter(Class<?> type, String name) {
      Class<?> current = type;
      while (current != null && current != Object.class) {
        try {
          Method method = current.getDeclaredMethod(name, Boolean.TYPE);
          if (method.getReturnType() == Void.TYPE) {
            return method;
          }
        } catch (NoSuchMethodException ignored) {
        }
        current = current.getSuperclass();
      }
      return null;
    }

    private static Field findBooleanField(Class<?> type, String name) {
      Class<?> current = type;
      while (current != null && current != Object.class) {
        try {
          Field field = current.getDeclaredField(name);
          if (field.getType() == Boolean.TYPE) {
            return field;
          }
        } catch (NoSuchFieldException ignored) {
        }
        current = current.getSuperclass();
      }
      return null;
    }
  }
}

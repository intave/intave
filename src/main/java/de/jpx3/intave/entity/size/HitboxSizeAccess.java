package de.jpx3.intave.entity.size;

import de.jpx3.intave.entity.type.EntityTypeDataAccessor;
import de.jpx3.intave.reflect.access.ReflectiveHandleAccess;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HitboxSizeAccess {
  private static final Map<Class<?>, HitboxSize> classCache = new ConcurrentHashMap<>();

  public static void setup() {
  }

  public static HitboxSize dimensionsOfBukkit(Entity entity) {
    HitboxSize boundingBoxSize = dimensionsOfBukkitBoundingBox(entity);
    if (usable(boundingBoxSize)) {
      rememberNativeSize(entity, boundingBoxSize);
      return boundingBoxSize;
    }
    return dimensionsOfNative(ReflectiveHandleAccess.handleOf(entity));
  }

  public static HitboxSize dimensionsOfNative(Object serverEntity) {
    if (serverEntity == null) {
      return HitboxSize.zero();
    }
    HitboxSize methodSize = dimensionsFromMethods(serverEntity);
    if (usable(methodSize)) {
      classCache.put(serverEntity.getClass(), methodSize);
      return methodSize;
    }
    HitboxSize fieldSize = dimensionsFromFields(serverEntity);
    if (usable(fieldSize)) {
      classCache.put(serverEntity.getClass(), fieldSize);
      return fieldSize;
    }
    return dimensionsOfNMSEntityClass(serverEntity.getClass());
  }

  public static HitboxSize dimensionsOfNMSEntityClass(Class<?> klass) {
    return classCache.computeIfAbsent(klass, HitboxSizeAccess::dimensionsFromClassName);
  }

  private static void rememberNativeSize(Entity entity, HitboxSize size) {
    Object handle = ReflectiveHandleAccess.handleOf(entity);
    if (handle != null) {
      classCache.put(handle.getClass(), size);
    }
  }

  private static HitboxSize dimensionsFromClassName(Class<?> klass) {
    HitboxSize size = EntityTypeDataAccessor.sizeByKey(entityKeyFromClass(klass));
    return usable(size) ? size : HitboxSize.zero();
  }

  private static String entityKeyFromClass(Class<?> klass) {
    String simpleName = klass.getSimpleName();
    if (simpleName.startsWith("Entity") && simpleName.length() > "Entity".length()) {
      simpleName = simpleName.substring("Entity".length());
    }
    if (simpleName.endsWith("Entity") && simpleName.length() > "Entity".length()) {
      simpleName = simpleName.substring(0, simpleName.length() - "Entity".length());
    }
    String key = camelToSnake(simpleName);
    if ("pig_zombie".equals(key)) {
      return "zombified_piglin";
    }
    if ("lava_slime".equals(key)) {
      return "magma_cube";
    }
    if ("snow_man".equals(key)) {
      return "snow_golem";
    }
    if ("villager_golem".equals(key)) {
      return "iron_golem";
    }
    if ("mushroom_cow".equals(key)) {
      return "mooshroom";
    }
    if ("ozelot".equals(key)) {
      return "ocelot";
    }
    if ("wither_boss".equals(key)) {
      return "wither";
    }
    if ("primed_tnt".equals(key)) {
      return "tnt";
    }
    if ("falling_sand".equals(key)) {
      return "falling_block";
    }
    if ("fireworks_rocket".equals(key)) {
      return "firework_rocket";
    }
    if ("thrown_enderpearl".equals(key)) {
      return "ender_pearl";
    }
    if ("eye_of_ender_signal".equals(key)) {
      return "eye_of_ender";
    }
    if ("thrown_potion".equals(key)) {
      return "potion";
    }
    if ("thrown_exp_bottle".equals(key)) {
      return "experience_bottle";
    }
    return key;
  }

  private static String camelToSnake(String input) {
    StringBuilder output = new StringBuilder(input.length() + 8);
    for (int index = 0; index < input.length(); index++) {
      char character = input.charAt(index);
      if (Character.isUpperCase(character) && index > 0) {
        output.append('_');
      }
      output.append(Character.toLowerCase(character));
    }
    return output.toString().toLowerCase(Locale.ROOT);
  }

  private static boolean usable(HitboxSize size) {
    return size != null && (size.width() > 0.0F || size.height() > 0.0F);
  }

  private static HitboxSize dimensionsOfBukkitBoundingBox(Entity entity) {
    try {
      Method getBoundingBox = entity.getClass().getMethod("getBoundingBox");
      Object boundingBox = getBoundingBox.invoke(entity);
      Method widthMethod = boundingBox.getClass().getMethod("getWidthX");
      Method heightMethod = boundingBox.getClass().getMethod("getHeight");
      double width = ((Number) widthMethod.invoke(boundingBox)).doubleValue();
      double height = ((Number) heightMethod.invoke(boundingBox)).doubleValue();
      return HitboxSize.of((float) width, (float) height);
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
      return null;
    }
  }

  private static HitboxSize dimensionsFromMethods(Object serverEntity) {
    Method widthMethod = findNumberMethod(serverEntity.getClass(), "getBbWidth");
    Method heightMethod = findNumberMethod(serverEntity.getClass(), "getBbHeight");
    if (widthMethod == null || heightMethod == null) {
      widthMethod = findNumberMethod(serverEntity.getClass(), "getWidth");
      heightMethod = findNumberMethod(serverEntity.getClass(), "getHeight");
    }
    if (widthMethod == null || heightMethod == null) {
      return null;
    }
    try {
      return HitboxSize.of(
        ((Number) widthMethod.invoke(serverEntity)).floatValue(),
        ((Number) heightMethod.invoke(serverEntity)).floatValue()
      );
    } catch (IllegalAccessException | InvocationTargetException ignored) {
      return null;
    }
  }

  private static HitboxSize dimensionsFromFields(Object serverEntity) {
    Field widthField = findNumberField(serverEntity.getClass(), "width");
    Field heightField = findNumberField(serverEntity.getClass(), "height");
    if (heightField == null) {
      heightField = findNumberField(serverEntity.getClass(), "length");
    }
    if (widthField == null || heightField == null) {
      return null;
    }
    try {
      return HitboxSize.of(
        ((Number) widthField.get(serverEntity)).floatValue(),
        ((Number) heightField.get(serverEntity)).floatValue()
      );
    } catch (IllegalAccessException ignored) {
      return null;
    }
  }

  private static Method findNumberMethod(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      try {
        Method method = current.getDeclaredMethod(name);
        if (method.getParameterCount() == 0 && Number.class.isAssignableFrom(box(method.getReturnType()))) {
          method.setAccessible(true);
          return method;
        }
      } catch (NoSuchMethodException ignored) {
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static Field findNumberField(Class<?> type, String name) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      try {
        Field field = current.getDeclaredField(name);
        if (Number.class.isAssignableFrom(box(field.getType()))) {
          field.setAccessible(true);
          return field;
        }
      } catch (NoSuchFieldException ignored) {
      }
      current = current.getSuperclass();
    }
    return null;
  }

  private static Class<?> box(Class<?> type) {
    if (!type.isPrimitive()) {
      return type;
    }
    if (type == Float.TYPE) {
      return Float.class;
    }
    if (type == Double.TYPE) {
      return Double.class;
    }
    if (type == Integer.TYPE) {
      return Integer.class;
    }
    if (type == Long.TYPE) {
      return Long.class;
    }
    if (type == Short.TYPE) {
      return Short.class;
    }
    if (type == Byte.TYPE) {
      return Byte.class;
    }
    return type;
  }
}

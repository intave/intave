package de.jpx3.intave.klass;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.klass.locate.Locate;
import de.jpx3.intave.library.asm.Type;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public final class Lookup {
  private static final String VERSION;

  static {
    String l;
    try {
      l = Bukkit.getServer().getClass().getPackage().getName().substring(23);
    } catch (Exception exception) {
      l = "v1_8_R3";
      System.out.println(Bukkit.getServer().getClass().getPackage().getName());
    }
    VERSION = l;
  }

  private static final String CRAFT_BUKKIT_PREFIX = "org.bukkit.craftbukkit." + VERSION;

  public static Method serverMethod(String classKey, String name, Class<?> returnType) {
    return serverMethod(classKey, name, new Class[0], returnType);
  }

  public static Method serverMethod(String classKey, String name, Class<?>[] arguments) {
    return serverMethod(classKey, name, arguments, Void.TYPE);
  }

  public static Method serverMethod(String classKey, String name, Class<?>[] arguments, Class<?> returnType) {
    Type[] argumentTypes = Arrays.stream(arguments).map(Type::getType).toArray(Type[]::new);
    return serverMethod(classKey, name + Type.getMethodDescriptor(Type.getType(returnType), argumentTypes));
  }

  public static Method serverMethod(String classKey, String name, Type[] arguments) {
    return serverMethod(classKey, name + Type.getMethodDescriptor(Type.VOID_TYPE, arguments));
  }

  public static Method serverMethod(String classKey, String name, Type[] arguments, Type returnType) {
    return serverMethod(classKey, name + Type.getMethodDescriptor(returnType, arguments));
  }

  public static Method serverMethod(String classKey, String methodKey) {
    return Locate.methodByKey(classKey, methodKey);
  }

  public static Field serverField(String serverClassName, String fieldName) {
    return Locate.fieldByKey(serverClassName, fieldName);
  }

  public static Class<?> serverClass(String key) {
    return Locate.classByKey(key);
  }

  public static Class<?> craftBukkitClass(String className) {
    return classByName(appendCraftBukkitPrefixToClass(className));
  }

  private static <T> Class<T> classByName(String className) {
    try {
      //noinspection unchecked
      return (Class<T>) Class.forName(className);
    } catch (ClassNotFoundException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  private static String appendCraftBukkitPrefixToClass(String className) {
    return CRAFT_BUKKIT_PREFIX + "." + className;
  }

  public static Field declaredFieldIn(Class<?> clazz, String name) {
    try {
      return clazz.getDeclaredField(name);
    } catch (NoSuchFieldException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  public static String version() {
    return VERSION;
  }
}

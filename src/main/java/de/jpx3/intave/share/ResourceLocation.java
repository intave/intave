package de.jpx3.intave.share;

import de.jpx3.intave.klass.locate.Locate;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class ResourceLocation {
  private final String namespace;
  private final String path;

  public ResourceLocation(String namespace, String path) {
    this.namespace = namespace;
    this.path = path;
  }

  public String namespace() {
    return namespace;
  }

  public String path() {
    return path;
  }

  private static final class ConstructorHolder {
    static final Constructor<?> NATIVE_RESOURCE_LOCATION_CONSTRUCTOR;
    static {
      Class<?> minecraftKeyClass = Locate.classByKey("MinecraftKey");
      try {
        NATIVE_RESOURCE_LOCATION_CONSTRUCTOR = minecraftKeyClass.getDeclaredConstructor(String.class, String.class);
        NATIVE_RESOURCE_LOCATION_CONSTRUCTOR.setAccessible(true);
      } catch (Exception exception) {
        throw new IllegalStateException(exception);
      }
    }
  }

  public Object toNativeResourceLocation() {
    try {
      return ConstructorHolder.NATIVE_RESOURCE_LOCATION_CONSTRUCTOR.newInstance(namespace, path);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }
}

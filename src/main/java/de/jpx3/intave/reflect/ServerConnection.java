/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.reflect;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServerConnection {
  private final Object handle;

  private ServerConnection(Object handle) {
    this.handle = handle;
  }

  public static ServerConnection find() {
    try {
      Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
      Object craftServer = bukkit.getMethod("getServer").invoke(null);
      if (craftServer == null) {
        throw new IllegalStateException("Bukkit has not initialized its server");
      }
      Object minecraftServer = readMemberByTypeName(craftServer, "MinecraftServer");
      if (minecraftServer == null) {
        throw new IllegalStateException("Could not find MinecraftServer from " + craftServer.getClass().getName());
      }
      Object serverConnection = readMemberByTypeName(
        minecraftServer,
        "ServerConnection",
        "ServerConnectionListener"
      );
      if (serverConnection == null) {
        throw new IllegalStateException("Could not find ServerConnection from " + minecraftServer.getClass().getName());
      }
      return new ServerConnection(serverConnection);
    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException failure) {
      throw new IllegalStateException("Could not access the Bukkit server", failure);
    } catch (InvocationTargetException failure) {
      throw new IllegalStateException("Bukkit failed to return its server", failure.getCause());
    }
  }

  public List<Channel> listeningChannels() {
    List<Channel> channels = new ArrayList<>();
    for (Field field : listeningChannelListFields()) {
      try {
        field.setAccessible(true);
        Object value = field.get(this.handle);
        if (!(value instanceof List)) {
          continue;
        }
        List<?> channelFutures = (List<?>) value;
        synchronized (channelFutures) {
          for (Object element : channelFutures) {
            if (element instanceof ChannelFuture) {
              channels.add(((ChannelFuture) element).channel());
            }
          }
        }
      } catch (IllegalAccessException failure) {
        throw new IllegalStateException("Could not read Minecraft listening channels", failure);
      }
    }
    return channels;
  }

  private List<Field> listeningChannelListFields() {
    List<Field> declaredChannelFutureLists = new ArrayList<>();
    List<Field> populatedChannelFutureLists = new ArrayList<>();
    for (Field field : fieldsInHierarchy(this.handle.getClass())) {
      if (Modifier.isStatic(field.getModifiers()) || !List.class.isAssignableFrom(field.getType())) {
        continue;
      }
      if (declaresChannelFutures(field.getGenericType())) {
        declaredChannelFutureLists.add(field);
        continue;
      }
      try {
        field.setAccessible(true);
        Object value = field.get(this.handle);
        if (value instanceof List && containsChannelFuture((List<?>) value)) {
          populatedChannelFutureLists.add(field);
        }
      } catch (IllegalAccessException | RuntimeException ignored) {
      }
    }
    return declaredChannelFutureLists.isEmpty() ? populatedChannelFutureLists : declaredChannelFutureLists;
  }

  private static Object readMemberByTypeName(Object target, String... expectedNames) {
    for (Method method : methodsInHierarchy(target.getClass())) {
      if (method.getParameterTypes().length != 0
        || Modifier.isStatic(method.getModifiers())
        || !hasTypeName(method.getReturnType(), expectedNames)) {
        continue;
      }
      try {
        method.setAccessible(true);
        Object value = method.invoke(target);
        if (value != null) {
          return value;
        }
      } catch (ReflectiveOperationException | RuntimeException ignored) {
      }
    }
    for (Field field : fieldsInHierarchy(target.getClass())) {
      if (Modifier.isStatic(field.getModifiers()) || !hasTypeName(field.getType(), expectedNames)) {
        continue;
      }
      try {
        field.setAccessible(true);
        Object value = field.get(target);
        if (value != null) {
          return value;
        }
      } catch (IllegalAccessException | RuntimeException ignored) {
      }
    }
    return null;
  }

  private static boolean hasTypeName(Class<?> type, String... expectedNames) {
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      for (String expectedName : expectedNames) {
        if (current.getSimpleName().equals(expectedName)) {
          return true;
        }
      }
      for (Class<?> implementedInterface : current.getInterfaces()) {
        if (hasTypeName(implementedInterface, expectedNames)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean declaresChannelFutures(Type genericType) {
    if (!(genericType instanceof ParameterizedType)) {
      return false;
    }
    for (Type type : ((ParameterizedType) genericType).getActualTypeArguments()) {
      if (isChannelFuture(type)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isChannelFuture(Type type) {
    if (type instanceof Class<?>) {
      return ChannelFuture.class.isAssignableFrom((Class<?>) type);
    }
    if (type instanceof ParameterizedType) {
      return isChannelFuture(((ParameterizedType) type).getRawType());
    }
    Type[] bounds;
    if (type instanceof WildcardType) {
      bounds = ((WildcardType) type).getUpperBounds();
    } else if (type instanceof TypeVariable<?>) {
      bounds = ((TypeVariable<?>) type).getBounds();
    } else {
      return false;
    }
    for (Type bound : bounds) {
      if (isChannelFuture(bound)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsChannelFuture(List<?> list) {
    synchronized (list) {
      for (Object element : list) {
        if (element instanceof ChannelFuture) {
          return true;
        }
      }
    }
    return false;
  }

  private static List<Field> fieldsInHierarchy(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      Collections.addAll(fields, current.getDeclaredFields());
    }
    return fields;
  }

  private static List<Method> methodsInHierarchy(Class<?> type) {
    List<Method> methods = new ArrayList<>();
    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
      Collections.addAll(methods, current.getDeclaredMethods());
    }
    return methods;
  }
}

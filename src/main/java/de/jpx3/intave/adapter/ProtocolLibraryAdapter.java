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

package de.jpx3.intave.adapter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.netty.WirePacket;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.InvalidDependencyException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.Arrays;

public final class ProtocolLibraryAdapter {

  private static final String PROTOCOLLIB_OUTDATED = "Your version of ProtocolLib is outdated";

  @Deprecated
  public static MinecraftVersion serverVersion() {
    return MinecraftVersion.current();
  }

  public static boolean protocolLibAvailable() {
    return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
  }

  /** Serializes only the packet payload. The caller must release the returned buffer. */
  public static ByteBuf serializePacket(PacketContainer packet) {
    MethodAccessor serializer = PacketSerialization.SERIALIZE_TO_BUFFER;
    if (serializer != null) {
      return (ByteBuf) serializer.invoke(packet);
    }
    return Unpooled.wrappedBuffer(WirePacket.bytesFromPacket(packet));
  }

  private static final class PacketSerialization {
    private static final MethodAccessor SERIALIZE_TO_BUFFER = findSerializer();

    private static MethodAccessor findSerializer() {
      try {
        // Select by the installed ProtocolLib API, not the Minecraft version.
        // Newer WirePacket helpers can expose unused backing-array capacity.
        return Accessors.getMethodAccessor(PacketContainer.class.getMethod("serializeToBuffer"));
      } catch (NoSuchMethodException exception) {
        // ProtocolLib 4.8 lacks this method; its WirePacket helper copies only readable bytes.
        return null;
      }
    }
  }

  public static void checkIfOutdated() {
    boolean temporaryPlayer = Arrays.stream(PacketEvent.class.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("isPlayerTemporary"));
    boolean specifiedEnumModifier = Arrays.stream(EnumWrappers.class.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("getGenericConverter") && method.getParameterCount() == 2);
    boolean byteBuddyExists = classExists("com.comphenix.net.bytebuddy.ByteBuddy");

    if (!specifiedEnumModifier) {
      throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing generic enum conversion)");
    }

    try {
      Class<?> minecraftKeyClass = MinecraftReflection.getMinecraftKeyClass();
    } catch (Throwable throwable) {
      throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing MinecraftKey class reference)");
    }

    if (!methodExists(MinecraftVersion.class.getName(), "atOrAbove")) {
      throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (atOrAbove check missing)");
    }

    if (!methodExistsInClassHierarchy(PacketContainer.class.getName(), "getMinecraftKeys")) {
      throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing minecraft key access)");
    }

    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      if (!methodExistsInClassHierarchy("com.comphenix.protocol.events.PacketContainer", "getMovingBlockPositions")) {
        throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing moving-object-position packet access)");
      }
    }

    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      if (!methodExistsInClassHierarchy(PacketContainer.class.getName(), "getEnumEntityUseActions")) {
        throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing enum entity use action access)");
      }

      if (MinecraftVersions.VER1_17_1.atOrAbove()) {
        if (!methodExistsInClassHierarchy(PacketContainer.class.getName(), "getIntLists")) {
          throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing int list access)");
        }
      }
    }

    if (!temporaryPlayer || !byteBuddyExists) {
      IntaveLogger.logger().info("Consider updating ProtocolLib");
    }
  }

  /** Bridge the renamed CraftBukkit mirror method until ProtocolLib handles it itself. */
  public static void prepareItemStackConversion() {
    if (MinecraftVersions.VER26_3.below()) {
      return;
    }
    ItemStack probe = new ItemStack(Material.STONE);
    Object nativeProbe = MinecraftReflection.getMinecraftItemStack(probe);
    try {
      verifyItemStackConversion(nativeProbe, probe);
      // Leave working ProtocolLib versions and an already-installed bridge untouched.
      return;
    } catch (RuntimeException originalFailure) {
      Throwable cause = originalFailure.getCause();
      if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().contains(".asCraftMirror(")) {
        throw originalFailure;
      }
      try {
        Method mirror = MinecraftReflection.getCraftItemStackClass().getMethod(
          "asBukkitMirror", MinecraftReflection.getItemStackClass());
        // All ProtocolLib item, list and equipment converters share this cached accessor.
        // The accessor belongs to ProtocolLib and only retains the CraftBukkit method.
        Field mirrorCache = MinecraftReflection.class.getDeclaredField("asCraftMirror");
        mirrorCache.setAccessible(true);
        Object previous = mirrorCache.get(null);
        mirrorCache.set(null, Accessors.getMethodAccessor(mirror));
        try {
          verifyItemStackConversion(nativeProbe, probe);
        } catch (RuntimeException verificationFailure) {
          mirrorCache.set(null, previous);
          throw verificationFailure;
        }
      } catch (ReflectiveOperationException | RuntimeException bridgeFailure) {
        originalFailure.addSuppressed(bridgeFailure);
        throw new InvalidDependencyException("Unable to adapt ProtocolLib item conversion for Minecraft 26.3", originalFailure);
      }
      IntaveLogger.logger().info("Adapted ProtocolLib item conversion for Minecraft 26.3");
    }
  }

  private static void verifyItemStackConversion(Object nativeItem, ItemStack expected) {
    ItemStack converted = MinecraftReflection.getBukkitItemStack(nativeItem);
    if (!expected.equals(converted)) {
      throw new IllegalStateException("ProtocolLib item conversion did not preserve the probe item");
    }
  }

  private static boolean classExists(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  private static boolean methodExistsInClassHierarchy(String className, String methodName) {
    try {
      Class<?> rootClass = Class.forName(className);
      do {
        if (methodExists(rootClass.getName(), methodName)) {
          return true;
        }
      } while ((rootClass = rootClass.getSuperclass()) != Object.class);
      return false;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  private static boolean methodExists(String className, String methodName) {
    try {
      Class.forName(className).getDeclaredMethod(methodName);
      return true;
    } catch (Exception exception) {
      return false;
    }
  }
}

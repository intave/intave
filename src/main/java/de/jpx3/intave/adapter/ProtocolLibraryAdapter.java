package de.jpx3.intave.adapter;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.access.InvalidDependencyException;
import org.bukkit.Bukkit;

import java.util.Arrays;

public final class ProtocolLibraryAdapter {
  private static final String PROTOCOLLIB_OUTDATED = "Your version of ProtocolLib is outdated";

  @Deprecated
  public static MinecraftVersion serverVersion() {
    return MinecraftVersion.getCurrentVersion();
  }

  public static boolean protocolLibAlreadyAvailable() {
    return Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;
  }

  public static void checkIfOutdated() {
    boolean temporaryPlayer = Arrays.stream(PacketEvent.class.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("isPlayerTemporary"));
    boolean specifiedEnumModifier = Arrays.stream(EnumWrappers.class.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("getGenericConverter") && method.getParameterCount() == 2);

    if (!specifiedEnumModifier) {
      throw new InvalidDependencyException(PROTOCOLLIB_OUTDATED + " (missing generic enum conversion)");
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

    if (!temporaryPlayer) {
      IntaveLogger.logger().info("Consider updating ProtocolLib");
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
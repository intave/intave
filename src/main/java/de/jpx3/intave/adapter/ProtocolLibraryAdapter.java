package de.jpx3.intave.adapter;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.EnumWrappers;
import de.jpx3.intave.access.InvalidDependencyException;
import de.jpx3.intave.logging.IntaveLogger;

import java.util.Arrays;

public final class ProtocolLibraryAdapter {
  public static MinecraftVersion serverVersion() {
    return ProtocolLibrary.getProtocolManager().getMinecraftVersion();
  }

  public static void checkIfOutdated() {
    boolean temporaryPlayer = Arrays.stream(PacketEvent.class.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("isPlayerTemporary"));
    boolean specifiedEnumModifier = Arrays.stream(EnumWrappers.class.getMethods()).anyMatch(method -> method.getName().equalsIgnoreCase("getGenericConverter") && method.getParameterCount() == 2);

    if (!specifiedEnumModifier) {
      throw new InvalidDependencyException("Your version of ProtocolLib is outdated (missing generic enum conversion)");
    }

    if (!methodExists(MinecraftVersion.class.getName(), "atOrAbove")) {
      throw new InvalidDependencyException("Your version of ProtocolLib is outdated (atOrAbove check missing)");
    }

    if (!methodExistsInClassHierarchy(PacketContainer.class.getName(), "getMinecraftKeys")) {
      throw new InvalidDependencyException("Your version of ProtocolLib is outdated (missing minecraft key access)");
    }

    if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      if (!methodExistsInClassHierarchy("com.comphenix.protocol.events.PacketContainer", "getMovingBlockPositions")) {
        throw new InvalidDependencyException("Your version of ProtocolLib is outdated (missing moving-object-position packet access)");
      }
    }

    if (!temporaryPlayer) {
      IntaveLogger.logger().info("Consider updating ProtocolLib");
    }
  }

  public static void setup() {
  }

  private static boolean methodExistsInClassHierarchy(String className, String methodName) {
    try {
      Class<?> rootClass = Class.forName(className);
      do {
        if(methodExists(rootClass.getName(), methodName)) {
          return true;
        }
      } while ((rootClass = rootClass.getSuperclass()) != Object.class);
      return false;
    } catch (ClassNotFoundException e) {
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
package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class InternalTeleportApplier {
  private final static boolean WEIRD_BOOLEAN_IN_INVOKE = MinecraftVersions.VER1_17_0.atOrAbove();
  private final Set<Object> teleportFlags = new HashSet<>();
  private final Method internalTeleportMethod;

  {
    try {
      teleportFlags.add(Lookup.serverField("PacketPlayOutPosition$EnumPlayerTeleportFlags", "X_ROT").get(null));
      teleportFlags.add(Lookup.serverField("PacketPlayOutPosition$EnumPlayerTeleportFlags", "Y_ROT").get(null));
      Class<?> playerConnectionClass = Lookup.serverClass("PlayerConnection");
      if (WEIRD_BOOLEAN_IN_INVOKE) {
        internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class, Boolean.TYPE);
      } else {
        internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class);
      }
      if (!internalTeleportMethod.isAccessible()) {
        internalTeleportMethod.setAccessible(true);
      }
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  public void teleport(Player player, Location dest, float yaw, float pitch, boolean rotationFlags) {
    try {
      User user = UserRepository.userOf(player);
      if (!user.hasPlayer()) {
        return;
      }
      Object playerConnection = user.playerConnection();
      Set<Object> rFlags = rotationFlags ? teleportFlags : Collections.emptySet();
      double posX = dest.getX();
      double posY = dest.getY();
      double posZ = dest.getZ();
      if (WEIRD_BOOLEAN_IN_INVOKE) {
        internalTeleportMethod.invoke(playerConnection, posX, posY, posZ, yaw, pitch, rFlags, false);
      } else {
        internalTeleportMethod.invoke(playerConnection, posX, posY, posZ, yaw, pitch, rFlags);
      }
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }
}

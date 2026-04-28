package de.jpx3.intave.module.mitigate;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.klass.rewrite.PatchyLoadingInjector;
import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

final class InternalTeleportApplier {
  private static final boolean WEIRD_BOOLEAN_IN_INVOKE = MinecraftVersions.VER1_17_0.atOrAbove() && !MinecraftVersions.VER1_19_4.atOrAbove();
  private static final boolean WITHOUT_SET = MinecraftVersions.VER1_21_3.atOrAbove();
  private final Method internalTeleportMethod;
  private TeleportApplier applier;

  {
    try {
      Class<?> playerConnectionClass = Lookup.serverClass("PlayerConnection");
      if (WITHOUT_SET) {
        String className = "de.jpx3.intave.module.mitigate.v214TeleportApplier";
        PatchyLoadingInjector.loadUnloadedClassPatched(IntavePlugin.class.getClassLoader(), className);
        try {
          applier = (TeleportApplier) Class.forName(className).newInstance();
        } catch (Exception exception) {
          throw new IntaveInternalException(exception);
        }

        // Unused
        internalTeleportMethod = null;
      } else if (WEIRD_BOOLEAN_IN_INVOKE) {
        internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class, Boolean.TYPE);
      } else {
        internalTeleportMethod = playerConnectionClass.getDeclaredMethod("internalTeleport", Double.TYPE, Double.TYPE, Double.TYPE, Float.TYPE, Float.TYPE, Set.class);
      }
      if (internalTeleportMethod != null && !internalTeleportMethod.isAccessible()) {
        internalTeleportMethod.setAccessible(true);
      }
    } catch (NoSuchMethodException exception) {
      throw new IntaveInternalException(exception);
    }
  }

  void teleport(Player player, Location dest, double motionY, float yaw, float pitch, boolean rotationFlags) {
    try {
      User user = UserRepository.userOf(player);
      if (!user.hasPlayer()) {
        return;
      }
      float fallDistance = player.getFallDistance();
      Object playerConnection = user.playerConnection();
      Set<?> rFlags = rotationFlags ? Relative.nativeSetOfNoRotationChange() : Collections.emptySet();
      double posX = dest.getX();
      double posY = dest.getY();
      double posZ = dest.getZ();
      if (WITHOUT_SET) {
        applier.teleport(player, posX, posY, posZ, yaw, pitch, rFlags);
      } else if (WEIRD_BOOLEAN_IN_INVOKE) {
        internalTeleportMethod.invoke(playerConnection, posX, posY, posZ, yaw, pitch, rFlags, false);
      } else {
        internalTeleportMethod.invoke(playerConnection, posX, posY, posZ, yaw, pitch, rFlags);
      }
      if (motionY > 0) {
        motionY = 0;
      }
      player.setFallDistance((float) (fallDistance + -motionY));
    } catch (InvocationTargetException | IllegalAccessException exception) {
      exception.printStackTrace();
    }
  }
}

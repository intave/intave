package de.jpx3.intave.tools.client;

import com.comphenix.protocol.utility.MinecraftVersion;
import de.jpx3.intave.adapter.ProtocolLibAdapter;
import de.jpx3.intave.reflect.ReflectionFailureException;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.UserMetaClientData;
import de.jpx3.intave.user.UserMetaMovementData;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static de.jpx3.intave.user.UserMetaClientData.PROTOCOL_VERSION_AQUATIC_UPDATE;

public final class PlayerMovementLocaleHelper {
  private final static boolean SERVER_SIDE_GLIDING = ProtocolLibAdapter.serverVersion().isAtLeast(ProtocolLibAdapter.COMBAT_UPDATE);
  private final static MethodType GLIDING_METHOD_TYPE = MethodType.methodType(Boolean.TYPE);
  private static MethodHandle methodHandleGliding;

  private static void loadGlidingMethodHandle(Player player) {
    try {
      methodHandleGliding = MethodHandles
        .lookup()
        .findVirtual(player.getClass(), "isGliding", GLIDING_METHOD_TYPE);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ReflectionFailureException(e);
    }
  }

  public static boolean flyingWithElytra(Player player) {
    if (SERVER_SIDE_GLIDING && methodHandleGliding == null) {
      loadGlidingMethodHandle(player);
    }
    try {
      return SERVER_SIDE_GLIDING && (boolean) methodHandleGliding.invoke(player);
    } catch (Throwable e) {
      throw new ReflectionFailureException(e);
    }
  }

  public static boolean isSwimming(Player player) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    UserMetaClientData clientData = meta.clientData();
    boolean b = canSwim(player);
//    player.sendMessage("canSwim=" + b);
    return clientData.protocolVersion() >= PROTOCOL_VERSION_AQUATIC_UPDATE && b && movementData.lastSprinting;
  }

  private static boolean canSwim(Player player) {
    User user = UserRepository.userOf(player);
    User.UserMeta meta = user.meta();
    UserMetaMovementData movementData = meta.movementData();
    return movementData.eyesInWater && movementData.inWater;
  }
}
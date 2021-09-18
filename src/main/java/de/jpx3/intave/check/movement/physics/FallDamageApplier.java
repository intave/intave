package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.clazz.Lookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class FallDamageApplier {
  private final MethodHandle fallDamageInvokeMethod;
  private final Object fallDamageSource;

  {
    Class<?> entityLivingClass = Lookup.serverClass("EntityLiving");
    // Search method name
    String methodName = "e";
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      methodName = "a";
    } else if (MinecraftVersions.VER1_14_0.atOrAbove()) {
      // >= 1.14
      methodName = "b";
    } else if (MinecraftVersions.VER1_13_0.atOrAbove()) {
      // 1.13
      methodName = "c";
    }
    // Search method descriptor
    MethodType methodType;
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      Class<?> damageSource = Lookup.serverClass("DamageSource");
      methodType = MethodType.methodType(Boolean.TYPE, Float.TYPE, Float.TYPE, damageSource);
    } else if (MinecraftVersions.VER1_15_0.atOrAbove()) {
      // >= 1.15
      methodType = MethodType.methodType(Boolean.TYPE, Float.TYPE, Float.TYPE);
    } else {
      methodType = MethodType.methodType(Void.TYPE, Float.TYPE, Float.TYPE);
    }
    try {
      fallDamageInvokeMethod = MethodHandles.lookup().findVirtual(entityLivingClass, methodName, methodType);
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new IllegalStateException(exception);
    }
    if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      try {
        fallDamageSource = Lookup.serverClass("DamageSource").getField("k").get(null);
      } catch (Exception exception) {
        throw new IntaveInternalException(exception);
      }
    } else {
      fallDamageSource = null;
    }
  }

  public void dealFallDamage(Player player, float fallDistance) {
    User user = UserRepository.userOf(player);
    Object playerHandle = user.playerHandle();
    try {
      if (MinecraftVersions.VER1_17_0.atOrAbove()) {
        fallDamageInvokeMethod.invoke(playerHandle, fallDistance, 1.0f, fallDamageSource);
      } else {
        fallDamageInvokeMethod.invoke(playerHandle, fallDistance, 1.0f);
      }
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
  }
}
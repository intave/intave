package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.access.IntaveInternalException;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.annotate.Relocate;
import de.jpx3.intave.klass.Lookup;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@Relocate
public final class FallDamageApplier {
  private final MethodHandle fallDamageInvokeMethod;
  private final Object fallDamageSource;

  {
    MethodHandle fallDamageInvokeMethod;
    Class<?> entityLivingClass = Lookup.serverClass("EntityLiving");
    // Search method name
    String methodName = "e";
    if (MinecraftVersions.VER1_21.atOrAbove()) {
      methodName = "causeFallDamage";
    } else if (MinecraftVersions.VER1_17_0.atOrAbove()) {
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
      fallDamageInvokeMethod = null;
    }
    this.fallDamageInvokeMethod = fallDamageInvokeMethod;
    if (MinecraftVersions.VER1_20.atOrAbove()) {
      Object source = null;
      try {
        World world = Bukkit.getWorlds().get(0);
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        Object damageSources = handle.getClass().getMethod("ag").invoke(handle);
        source = Lookup.serverClass("DamageSources").getMethod("k").invoke(damageSources);
      } catch (Exception exception) {
//        throw new IntaveInternalException(exception);
//        fallDamageSource = null;
      }
      fallDamageSource = source;
    } else if (MinecraftVersions.VER1_19_4.atOrAbove()) {
      try {
        World world = Bukkit.getWorlds().get(0);
        Object handle = world.getClass().getMethod("getHandle").invoke(world);
        Object damageSources = handle.getClass().getMethod("af").invoke(handle);
        fallDamageSource = Lookup.serverClass("DamageSources").getMethod("k").invoke(damageSources);
      } catch (Exception exception) {
        throw new IntaveInternalException(exception);
      }
    } else if (MinecraftVersions.VER1_17_0.atOrAbove()) {
      try {
        fallDamageSource = Lookup.serverClass("DamageSource").getField("k").get(null);
      } catch (Exception exception) {
        throw new IntaveInternalException(exception);
      }
      if (!fallDamageSource.toString().toLowerCase().contains("fall")) {
        throw new IllegalStateException("DamageSource.k is not the fall damage source");
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

  /*
    MobEffect mobeffect = this.b(MobEffects.h);
    float f2 = mobeffect == null ? 0.0F : (float)(mobeffect.c() + 1);
    return MathHelper.f((f - 3.0F - f2) * f1);
   */
  public float distanceToDamage(Player player, float fallDistance) {
    User user = UserRepository.userOf(player);
    return (float) f((fallDistance - 3.0f - jumpBoostModifierOf(user)));
  }

  private float jumpBoostModifierOf(User user) {
    EffectMetadata potionData = user.meta().potions();
    int potionDuration = potionData.potionEffectJumpDuration;
    boolean infiniteEffectsAllowed = user.protocolVersion() >= 763;
    int jumpBoostLevel = potionData.potionEffectJumpAmplifier();
    float jumpBoostModifier;
    if (potionDuration > 0 || potionDuration == -1 && infiniteEffectsAllowed) {
      jumpBoostModifier = (float) (jumpBoostLevel + 1);
    } else {
      jumpBoostModifier = 0.0f;
    }
    return jumpBoostModifier;
  }

  private static int f(float var0) {
    int var1 = (int)var0;
    return var0 > (float)var1 ? var1 + 1 : var1;
  }

  public float damageToDistance(Player player, float damage) {
    User user = UserRepository.userOf(player);
    return g(damage) + 3.0f + jumpBoostModifierOf(user);
  }

  private static float g(float var0) {
    return var0 % 1 == 0 ? var0 : (float) Math.floor(var0);
  }
  public float remainingDistance(Player player, float seenDamage, float predictedDamage) {
    return damageToDistance(player, predictedDamage) - damageToDistance(player, seenDamage);
  }
}
package de.jpx3.intave.check.movement.physics;

import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.EffectMetadata;
import org.bukkit.entity.Player;

public final class FallDamageApplier {
  public void dealFallDamage(Player player, float fallDistance) {
    float damage = distanceToDamage(player, fallDistance);
    if (damage > 0.0F) {
      player.damage(damage);
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

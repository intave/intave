package de.jpx3.intave.tools.client;

import de.jpx3.intave.annotate.refactoring.IdoNotBelongHere;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import de.jpx3.intave.user.meta.ProtocolMetadata;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import static de.jpx3.intave.user.meta.ProtocolMetadata.VER_1_13;

@IdoNotBelongHere
public final class EffectLogic {
  public final static PotionEffectType EFFECT_LEVITATION = PotionEffectType.getByName("LEVITATION");
  private final static PotionEffectType EFFECT_SLOW_FALLING = PotionEffectType.getByName("SLOW_FALLING");
  private final static PotionEffectType EFFECT_DOLPHIN = PotionEffectType.getByName("DOLPHINS_GRACE");

  public static int effectAmplifier(Player player, PotionEffectType type) {
    for (PotionEffect activeEffect : player.getActivePotionEffects()) {
      if (activeEffect.getType().equals(type)) {
        return activeEffect.getAmplifier();
      }
    }
    return 0;
  }

  public static boolean isPotionLevitationActive(Player player) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (EFFECT_LEVITATION == null || clientData.protocolVersion() < 107) {
      return false;
    }
    return isPotionActive(player, EFFECT_LEVITATION);
  }

  public static boolean isPotionDolphinActive(Player player) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (EFFECT_DOLPHIN == null || clientData.protocolVersion() < VER_1_13) {
      return false;
    }
    return isPotionActive(player, EFFECT_DOLPHIN);
  }

  public static boolean isPotionSlowFallingActive(Player player) {
    User user = UserRepository.userOf(player);
    ProtocolMetadata clientData = user.meta().protocol();
    if (EFFECT_SLOW_FALLING == null || clientData.protocolVersion() < 393) {
      return false;
    }
    return isPotionActive(player, EFFECT_SLOW_FALLING);
  }

  public static boolean isPotionActive(Player player, PotionEffectType type) {
    for (PotionEffect activePotionEffect : player.getActivePotionEffects()) {
      if (activePotionEffect.getType().equals(type)) {
        return true;
      }
    }
    return false;
  }
}
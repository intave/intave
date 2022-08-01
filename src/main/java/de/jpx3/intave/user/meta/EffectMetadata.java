package de.jpx3.intave.user.meta;

import de.jpx3.intave.annotate.Relocate;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;

import static de.jpx3.intave.module.tracker.player.EffectTracker.*;

@Relocate
public final class EffectMetadata {
  private int potionEffectSpeedAmplifier = 0;
  public int potionEffectSpeedDuration = 0;

  private int potionEffectSlownessAmplifier = 0;
  public int potionEffectSlownessDuration = 0;

  private int potionEffectJumpAmplifier = 0;
  public int potionEffectJumpDuration = 0;

  public EffectMetadata(Player player) {
    if (player != null) {
      loadPotionEffectsFrom(player.getActivePotionEffects());
    }
  }

  private void loadPotionEffectsFrom(Collection<? extends PotionEffect> potionEffects) {
    for (PotionEffect potionEffect : potionEffects) {
      inspectEffect(potionEffect);
    }
  }

  private void inspectEffect(PotionEffect potionEffect) {
    int duration = potionEffect.getDuration();
    int amplifier = potionEffect.getAmplifier();
    switch (potionEffect.getType().getId()) {
      case POTION_EFFECT_SPEED: {
        potionEffectSpeedDuration = duration;
        potionEffectSpeedAmplifier = amplifier;
        break;
      }
      case POTION_EFFECT_SLOWNESS: {
        potionEffectSlownessDuration = duration;
        potionEffectSlownessAmplifier = amplifier;
        break;
      }
      case POTION_EFFECT_JUMP_BOOST: {
        potionEffectJumpDuration = duration;
        potionEffectJumpAmplifier = amplifier;
        break;
      }
    }
  }

  public void clearPotionEffects() {
    potionEffectSpeedDuration = 0;
    potionEffectSpeedAmplifier = 0;
    potionEffectSlownessDuration = 0;
    potionEffectSlownessAmplifier = 0;
    potionEffectJumpDuration = 0;
    potionEffectJumpAmplifier = 0;
  }

  public int potionEffectSpeedAmplifier() {
    return potionEffectSpeedAmplifier;
  }

  public int potionEffectSlownessAmplifier() {
    return potionEffectSlownessAmplifier;
  }

  public int potionEffectJumpAmplifier() {
    return potionEffectJumpAmplifier;
  }

  public void potionEffectSpeedAmplifier(int potionEffectSpeedAmplifier) {
    this.potionEffectSpeedAmplifier = potionEffectSpeedAmplifier;
  }

  public void potionEffectSlownessAmplifier(int potionEffectSlownessAmplifier) {
    this.potionEffectSlownessAmplifier = potionEffectSlownessAmplifier;
  }

  public void potionEffectJumpAmplifier(int potionEffectJumpAmplifier) {
    this.potionEffectJumpAmplifier = potionEffectJumpAmplifier;
  }
}
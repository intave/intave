/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.module.tracker.player;

import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.EntityEffectPacket;
import de.jpx3.intave.packet.contract.immutable.RemoveEntityEffectPacket;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.share.MinecraftKey;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.EffectMetadata;

import static de.jpx3.intave.packet.ListenerPriority.LATER;

public final class EffectTracker extends Module {
  public static final int POTION_EFFECT_SPEED = 1;
  public static final int POTION_EFFECT_SLOWNESS = 2;
  public static final int POTION_EFFECT_JUMP_BOOST = 8;

  private static final MinecraftKey SPEED = MinecraftKey.withDefaultNamespace("speed");
  private static final MinecraftKey SLOWNESS = MinecraftKey.withDefaultNamespace("slowness");
  private static final MinecraftKey JUMP_BOOST = MinecraftKey.withDefaultNamespace("jump_boost");

  @OnSend(priority = LATER)
  public void sentEffect(User user, EntityEffectPacket packet, PacketSendEvent event) {
    if (event.isCancelled() || packet.entityId() != user.player().getEntityId()) {
      return;
    }
    MinecraftKey effectType = packet.effectType();
    int amplifier = packet.effectAmplifier();
    int duration = packet.effectDuration();
    Modules.feedback().synchronize(
      user, event, () -> receiveEffect(user, effectType, amplifier, duration)
    );
  }

  @OnSend(priority = LATER)
  public void sentRemoveEffect(User user, RemoveEntityEffectPacket packet, PacketSendEvent event) {
    if (event.isCancelled() || packet.entityId() != user.player().getEntityId()) {
      return;
    }
    MinecraftKey effectType = packet.effectType();
    Modules.feedback().synchronize(
      user, event, () -> receiveEffectRemoval(user, effectType)
    );
  }

  private void receiveEffectRemoval(User user, MinecraftKey effectType) {
    EffectMetadata potionData = user.meta().potions();
    if (SPEED.equals(effectType)) {
      potionData.potionEffectSpeedAmplifier(0);
      potionData.potionEffectSpeedDuration = 0;
    } else if (SLOWNESS.equals(effectType)) {
      potionData.potionEffectSlownessAmplifier(0);
      potionData.potionEffectSlownessDuration = 0;
    } else if (JUMP_BOOST.equals(effectType)) {
      potionData.potionEffectJumpAmplifier(0);
      potionData.potionEffectJumpDuration = 0;
    }
  }

  private void receiveEffect(User user, MinecraftKey effectType, int effectAmplifier, int effectDuration) {
    EffectMetadata potionData = user.meta().potions();

    boolean infiniteEffectsAllowed = user.protocolVersion() >= 763;
    if (effectDuration == -1 && infiniteEffectsAllowed) {
      effectDuration = Integer.MAX_VALUE;
    }

    if (SPEED.equals(effectType)) {
      potionData.potionEffectSpeedAmplifier(effectAmplifier + 1);
      potionData.potionEffectSpeedDuration = effectDuration - 1;
    } else if (SLOWNESS.equals(effectType)) {
      potionData.potionEffectSlownessAmplifier(effectAmplifier + 1);
      potionData.potionEffectSlownessDuration = effectDuration - 1;
    } else if (JUMP_BOOST.equals(effectType)) {
      potionData.potionEffectJumpAmplifier(effectAmplifier);
      potionData.potionEffectJumpDuration = effectDuration - 1;
    }
  }
}

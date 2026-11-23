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

import ac.intave.samples.event.PlayerVitalsEvent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packetperfect.OnSend;
import de.jpx3.intave.packet.contract.immutable.ExperiencePacket;
import de.jpx3.intave.packet.contract.immutable.UpdateHealthPacket;
import de.jpx3.intave.packet.event.PacketSendEvent;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;

public final class PlayerVitalsTracker extends Module {
  @OnSend
  public void sentHealth(User user, UpdateHealthPacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    float health = packet.health();
    int foodLevel = packet.foodLevel();
    float saturation = packet.saturation();

    Modules.feedback().synchronize(user, event, () -> {
      if (foodLevel <= 6) {
        user.meta().movement().setSprinting(false);
      }
      user.meta().abilities().synchronizeVitals(health, foodLevel, saturation);
      emit(user);
    });
  }

  @OnSend
  public void sentExperience(User user, ExperiencePacket packet, PacketSendEvent event) {
    if (event.isCancelled()) {
      return;
    }
    float progress = packet.progress();
    int level = packet.level();
    int totalExperience = packet.totalExperience();

    Modules.feedback().synchronize(user, event, () -> {
      user.meta().abilities().synchronizeExperience(progress, level, totalExperience);
      emit(user);
    });
  }

  public void synchronizeSnapshot(User user) {
    user.tickFeedback(() -> emit(user));
  }

  private static void emit(User user) {
    AbilityMetadata abilities = user.meta().abilities();
    Modules.nayoro().emit(user, event(abilities));
  }

  static PlayerVitalsEvent event(AbilityMetadata abilities) {
    synchronized (abilities) {
      return new PlayerVitalsEvent(
        abilities.health, abilities.foodLevel, abilities.saturation,
        abilities.experienceProgress, abilities.experienceLevel,
        abilities.totalExperience,
        optionalValue(abilities.maxHealth()),
        null, null,
        optionalValue(abilities.armor()),
        optionalValue(abilities.armorToughness())
      );
    }
  }

  private static Double optionalValue(double value) {
    return Double.isFinite(value) ? value : null;
  }
}

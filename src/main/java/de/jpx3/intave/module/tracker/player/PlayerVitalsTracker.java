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
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.module.Module;
import de.jpx3.intave.module.Modules;
import de.jpx3.intave.module.linker.packet.Engine;
import de.jpx3.intave.module.linker.packet.PacketSubscription;
import de.jpx3.intave.packet.plib.reader.ExperienceReader;
import de.jpx3.intave.packet.plib.reader.UpdateHealthReader;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.meta.AbilityMetadata;

import static de.jpx3.intave.module.feedback.FeedbackOptions.SELF_SYNCHRONIZATION;
import static de.jpx3.intave.packet.contract.PacketType.Server.EXPERIENCE;
import static de.jpx3.intave.packet.contract.PacketType.Server.UPDATE_HEALTH;

public final class PlayerVitalsTracker extends Module {
  @PacketSubscription(
    engine = Engine.INTERNAL,
    packetsOut = UPDATE_HEALTH
  )
  public void sentHealth(
    User user, PacketEvent event, UpdateHealthReader reader
  ) {
    float health = reader.health();
    int foodLevel = reader.foodLevel();
    float saturation = reader.saturation();

    user.packetTickFeedback(event, () -> {
      if (foodLevel <= 6) {
        user.meta().movement().setSprinting(false);
      }
      user.meta().abilities().synchronizeVitals(health, foodLevel, saturation);
      emit(user);
    }, SELF_SYNCHRONIZATION);
  }

  @PacketSubscription(
    engine = Engine.INTERNAL,
    packetsOut = EXPERIENCE
  )
  public void sentExperience(
    User user, PacketEvent event, ExperienceReader reader
  ) {
    float progress = reader.progress();
    int level = reader.level();
    int totalExperience = reader.totalExperience();

    user.packetTickFeedback(event, () -> {
      user.meta().abilities().synchronizeExperience(progress, level, totalExperience);
      emit(user);
    }, SELF_SYNCHRONIZATION);
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

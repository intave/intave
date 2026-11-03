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

package de.jpx3.intave.packet.plib.reader;

import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.potion.PotionEffectType;

import static de.jpx3.intave.adapter.MinecraftVersions.VER1_21;

public final class EntityEffectReader extends EntityReader {
  private static final boolean SECURE_CHAT = MinecraftVersions.VER1_19.atOrAbove();
  private static final boolean CAVE_AND_CLIFFS = MinecraftVersions.VER1_18_2.atOrAbove();

  public int effectType() {
    PacketContainer packet = packet();
    if (SECURE_CHAT) {
      PotionEffectType potionEffectType = packet.getEffectTypes().readSafely(0);
      if (potionEffectType == null) {
        return 0;
      }
      //noinspection deprecation
      return potionEffectType.getId();
    } else if (CAVE_AND_CLIFFS) {
      Integer potionEffectType = packet.getIntegers().readSafely(1);
      if (potionEffectType == null) {
        return 0;
      }
      return potionEffectType;
    } else {
      Byte potionEffectType = packet.getBytes().readSafely(0);
      if (potionEffectType == null) {
        potionEffectType = 0;
      }
      return potionEffectType;
    }
  }

  public int effectAmplifier() {
    PacketContainer packet = packet();
    if (VER1_21.atOrAbove()) {
      Integer potionEffectAmplifier = packet.getIntegers().readSafely(1);
      if (potionEffectAmplifier == null) {
        potionEffectAmplifier = 0;
      }
      return potionEffectAmplifier;
    } else {
      Byte potionEffectAmplifier = packet.getBytes().readSafely(CAVE_AND_CLIFFS ? 0 : 1);
      if (potionEffectAmplifier == null) {
        potionEffectAmplifier = 0;
      }
      return potionEffectAmplifier;
    }
  }

  public int effectDuration() {
    int fieldIndex = VER1_21.atOrAbove() ? 3 : (CAVE_AND_CLIFFS && !SECURE_CHAT ? 2 : 1);
    Integer potionEffectDuration = packet().getIntegers().readSafely(fieldIndex);
    if (potionEffectDuration == null) {
      potionEffectDuration = 0;
    }
    return potionEffectDuration;
  }
}

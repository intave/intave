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

package de.jpx3.intave.packet.wire.entity.effect;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

final class EffectTypeRegistry {
	// MobEffects keeps this order; 1.20.2 removed the unused registry entry at ID 0.
	private static final String[] NAMES = {
		"speed", "slowness", "haste", "mining_fatigue", "strength", "instant_health", "instant_damage",
		"jump_boost", "nausea", "regeneration", "resistance", "fire_resistance", "water_breathing",
		"invisibility", "blindness", "night_vision", "hunger", "weakness", "poison", "wither",
		"health_boost", "absorption", "saturation", "glowing", "levitation", "luck", "unluck",
		"slow_falling", "conduit_power", "dolphins_grace", "bad_omen", "hero_of_the_village", "darkness",
		"trial_omen", "raid_omen", "wind_charged", "weaving", "oozing", "infested", "breath_of_the_nautilus"
	};
	static final EffectTypeRegistry LEGACY = new EffectTypeRegistry(1);
	static final EffectTypeRegistry MODERN = new EffectTypeRegistry(0);

	private final List<MinecraftKey> keys = new ArrayList<>();
	private final int firstId;

	private EffectTypeRegistry(int firstId) {
		this.firstId = firstId;
		for (String name : NAMES) keys.add(MinecraftKey.withDefaultNamespace(name));
	}

	StreamCodec<ByteBuf, ByteBuf, MinecraftKey> codec(StreamCodec<ByteBuf, ByteBuf, Integer> ids) {
		return ids.beforeAndAfter(this::keyById, this::idOf);
	}

	private MinecraftKey keyById(int id) {
		int index = id - firstId;
		if (index < 0 || index >= keys.size()) throw new IllegalArgumentException("Unknown effect ID " + id);
		return keys.get(index);
	}

	private int idOf(MinecraftKey key) {
		int index = keys.indexOf(key);
		if (index < 0) throw new IllegalArgumentException("Unknown effect " + key);
		return index + firstId;
	}
}

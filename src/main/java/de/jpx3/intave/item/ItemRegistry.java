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

package de.jpx3.intave.item;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.registry.ProtocolRegistryFiles;
import de.jpx3.intave.share.MinecraftKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static de.jpx3.intave.codec.JsonStreamCodecs.INTEGER;
import static de.jpx3.intave.codec.JsonStreamCodecs.stringMapCodec;

public final class ItemRegistry {
	private static final ProtocolRegistryFiles<ItemRegistry> TABLES = new ProtocolRegistryFiles<>(
		"item_ids", stringMapCodec(INTEGER), ItemRegistry::new
	);

	private final MinecraftKey[] keysById;
	private final Map<MinecraftKey, Integer> idsByKey;

	private ItemRegistry(Map<String, Integer> entries) {
		keysById = new MinecraftKey[Collections.max(entries.values()) + 1];
		Map<MinecraftKey, Integer> ids = new HashMap<>();
		entries.forEach((name, id) -> {
			MinecraftKey key = MinecraftKey.from(name);
			if (id < 0 || keysById[id] != null) {
				throw new IllegalStateException("Invalid item ID " + id + " for " + name);
			}
			keysById[id] = key;
			ids.put(key, id);
		});
		idsByKey = Collections.unmodifiableMap(ids);
	}

	public static ItemRegistry forProtocol(int protocolVersion) {
		return TABLES.forProtocol(protocolVersion);
	}

	public static Set<Integer> supportedProtocolVersions() {
		return TABLES.supportedProtocolVersions();
	}

	/** Returns null for an unregistered wire ID, including gaps in the legacy registry. */
	public @Nullable MinecraftKey keyById(int id) {
		return id >= 0 && id < keysById.length ? keysById[id] : null;
	}

	/** Returns -1 when this protocol has no item registered under the given key. */
	public int idOf(MinecraftKey key) {
		return idsByKey.getOrDefault(key, -1);
	}

	public Map<MinecraftKey, Integer> itemIds() {
		return idsByKey;
	}
}

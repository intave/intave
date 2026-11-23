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

import java.util.*;

import static de.jpx3.intave.codec.JsonStreamCodecs.STRING;
import static de.jpx3.intave.codec.JsonStreamCodecs.listCodecOf;

/** Vanilla minecraft:data_component_type wire IDs for a client protocol. */
public final class ItemComponentTypeRegistry {
	private static final ProtocolRegistryFiles<ItemComponentTypeRegistry> TABLES = new ProtocolRegistryFiles<>(
		"item_component_types", listCodecOf(STRING), ItemComponentTypeRegistry::new
	);

	private final MinecraftKey[] keysById;
	private final Map<MinecraftKey, Integer> idsByKey;

	private ItemComponentTypeRegistry(List<String> types) {
		keysById = new MinecraftKey[types.size()];
		Map<MinecraftKey, Integer> ids = new LinkedHashMap<>();
		for (int id = 0; id < types.size(); id++) {
			MinecraftKey key = MinecraftKey.from(types.get(id));
			if (ids.put(key, id) != null) {
				throw new IllegalStateException("Duplicate item component type " + key);
			}
			keysById[id] = key;
		}
		idsByKey = Collections.unmodifiableMap(ids);
	}

	public static ItemComponentTypeRegistry forProtocol(int protocolVersion) {
		return TABLES.forProtocol(protocolVersion);
	}

	public static Set<Integer> supportedProtocolVersions() {
		return TABLES.supportedProtocolVersions();
	}

	public @Nullable MinecraftKey keyById(int id) {
		return id >= 0 && id < keysById.length ? keysById[id] : null;
	}

	public int idOf(MinecraftKey key) {
		return idsByKey.getOrDefault(key, -1);
	}

	public Map<MinecraftKey, Integer> componentTypeIds() {
		return idsByKey;
	}
}

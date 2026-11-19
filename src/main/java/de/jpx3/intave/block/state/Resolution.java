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

package de.jpx3.intave.block.state;

import de.jpx3.intave.block.variant.BlockVariantRegister;
import de.jpx3.intave.share.MaterialAndVariant;
import de.jpx3.intave.version.ServerProtocolVersion;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class Resolution {
	private static volatile Cache cache = new Cache(-1);

	private Resolution() {
	}

	static synchronized void invalidateCache() {
		cache = new Cache(-1);
	}

	static Optional<MaterialAndVariant> resolve(int protocolVersion, int stateId) {
		int serverProtocol = ServerProtocolVersion.current();
		Cache current = cache;
		if (current.serverProtocol != serverProtocol) {
			synchronized (Resolution.class) {
				current = cache;
				if (current.serverProtocol != serverProtocol) {
					cache = current = new Cache(serverProtocol);
				}
			}
		}
		Cache active = current;
		long key = (long) protocolVersion << 32 | stateId & 0xffffffffL;
		return active.states.computeIfAbsent(key, ignored -> resolveUncached(protocolVersion, stateId, active));
	}

	private static Optional<MaterialAndVariant> resolveUncached(int protocol, int stateId, Cache cache) {
		BlockState target = BlockStates.translate(protocol, stateId, cache.serverProtocol);
		Material material = target.legacy() ? Material.getMaterial(target.id() >>> 4)
			: Material.getMaterial(target.nameKey().path().toUpperCase(Locale.ROOT));
		if (material == null) {
			return Optional.empty();
		}
		int variant;
		if (target.legacy()) {
			variant = target.legacyVariantId();
			if (!BlockVariantRegister.variantIdsOf(material).contains(variant)) {
				throw new IllegalStateException("Block variants are not indexed for " + material + " metadata " + variant);
			}
		} else {
			Map<Map<String, String>, Integer> variants = cache.variants.computeIfAbsent(material, Resolution::indexVariants);
			Integer found = variants.get(target.properties());
			if (found == null) {
				throw new UnsupportedOperationException("Server's Intave variant register has no state matching " + target);
			}
			variant = found;
		}
		return Optional.of(new MaterialAndVariant(material, variant));
	}

	private static synchronized Map<Map<String, String>, Integer> indexVariants(Material material) {
		Set<Integer> ids = BlockVariantRegister.variantIdsOf(material);
		if (ids.isEmpty()) {
			throw new IllegalStateException("Block variants have not been indexed for " + material);
		}
		Map<Map<String, String>, Integer> result = new HashMap<>();
		boolean piston = material.name().equals("PISTON_HEAD") || material.name().equals("MOVING_PISTON");
		for (int id : ids) {
			Map<String, String> properties = new TreeMap<>();
			BlockVariantRegister.propertiesOf(material, id).forEach((name, value) -> {
				String key = name.toLowerCase(Locale.ROOT);
				String serialized = (value instanceof Enum<?> ? ((Enum<?>) value).name() : String.valueOf(value)).toLowerCase(Locale.ROOT);
				// Minecraft serializes PistonType.DEFAULT as "normal".
				if (piston && key.equals("type") && serialized.equals("default")) {
					serialized = "normal";
				}
				properties.put(key, serialized);
			});
			// The register can expose zero as an alias of the first native variant.
			result.merge(Collections.unmodifiableMap(properties), id, Math::min);
		}
		return Collections.unmodifiableMap(result);
	}

	private static final class Cache {
		private final int serverProtocol;
		private final Map<Long, Optional<MaterialAndVariant>> states = new ConcurrentHashMap<>();
		private final Map<Material, Map<Map<String, String>, Integer>> variants = new ConcurrentHashMap<>();

		private Cache(int serverProtocol) {
			this.serverProtocol = serverProtocol;
		}
	}
}


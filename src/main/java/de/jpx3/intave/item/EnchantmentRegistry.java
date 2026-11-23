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

import com.google.common.collect.ImmutableBiMap;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.registry.ProtocolRegistryFiles;
import de.jpx3.intave.share.MinecraftKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.jpx3.intave.codec.JsonStreamCodecs.INTEGER;
import static de.jpx3.intave.codec.JsonStreamCodecs.stringMapCodec;

public final class EnchantmentRegistry {
	private static final ProtocolRegistryFiles<EnchantmentRegistry> VANILLA = new ProtocolRegistryFiles<>(
		"enchantments", stringMapCodec(INTEGER), EnchantmentRegistry::fromVanillaIds
	);

	private final ImmutableBiMap<MinecraftKey, Integer> idsByKey;

	private EnchantmentRegistry(ImmutableBiMap<MinecraftKey, Integer> idsByKey) {
		this.idsByKey = idsByKey;
	}

	/**
	 * Returns the vanilla table. From 1.21 onward a connection can advertise a different order.
	 */
	public static EnchantmentRegistry forProtocol(int protocolVersion) {
		return VANILLA.forProtocol(protocolVersion);
	}

	/**
	 * Builds the connection's registry from every entry in its registry-data packet, in wire order.
	 */
	public static EnchantmentRegistry fromNetworkOrder(List<MinecraftKey> entries) {
		ImmutableBiMap.Builder<MinecraftKey, Integer> ids = ImmutableBiMap.builder();
		int id = 0;
		for (MinecraftKey key : entries) {
			ids.put(key, id++);
		}
		return new EnchantmentRegistry(ids.build());
	}

	public static Set<Integer> supportedProtocolVersions() {
		return VANILLA.supportedProtocolVersions();
	}

	/**
	 * Returns null for an unregistered ID, including gaps in legacy tables.
	 */
	public @Nullable MinecraftKey keyById(int id) {
		return idsByKey.inverse().get(id);
	}

	/**
	 * Returns -1 for an unregistered enchantment.
	 */
	public int idOf(MinecraftKey key) {
		return idsByKey.getOrDefault(key, -1);
	}

	public Map<MinecraftKey, Integer> enchantmentIds() {
		return idsByKey;
	}

	private static EnchantmentRegistry fromVanillaIds(Map<String, Integer> entries) {
		ImmutableBiMap.Builder<MinecraftKey, Integer> ids = ImmutableBiMap.builder();
		entries.forEach((name, id) -> ids.put(MinecraftKey.from(name), id));
		return new EnchantmentRegistry(ids.build());
	}
}

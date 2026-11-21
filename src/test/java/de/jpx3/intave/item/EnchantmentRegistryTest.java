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

import de.jpx3.intave.share.MinecraftKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class EnchantmentRegistryTest {
	private static final MinecraftKey PROTECTION = MinecraftKey.withDefaultNamespace("protection");
	private static final MinecraftKey SHARPNESS = MinecraftKey.withDefaultNamespace("sharpness");

	@Test
	void everySupportedProtocolHasABidirectionalVanillaTable() {
		assertEquals(ItemRegistry.supportedProtocolVersions(), EnchantmentRegistry.supportedProtocolVersions());
		for (int protocol : EnchantmentRegistry.supportedProtocolVersions()) {
			EnchantmentRegistry registry = EnchantmentRegistry.forProtocol(protocol);
			registry.enchantmentIds().forEach((key, id) -> {
				assertEquals(key, registry.keyById(id), "protocol " + protocol);
				assertEquals(id.intValue(), registry.idOf(key));
			});
			assertNull(registry.keyById(-1));
			assertNull(registry.keyById(Integer.MAX_VALUE));
			assertEquals(-1, registry.idOf(new MinecraftKey("example", "protection")));
		}
		assertThrows(IllegalArgumentException.class, () -> EnchantmentRegistry.forProtocol(48));
	}

	@Test
	void idsAndNamesMatchTheirProtocolBoundaries() {
		assertEquals(16, EnchantmentRegistry.forProtocol(47).idOf(SHARPNESS));
		assertNull(EnchantmentRegistry.forProtocol(47).keyById(9));
		assertEquals(MinecraftKey.withDefaultNamespace("frost_walker"), EnchantmentRegistry.forProtocol(107).keyById(9));
		assertNull(EnchantmentRegistry.forProtocol(315).keyById(22));
		MinecraftKey sweeping = MinecraftKey.withDefaultNamespace("sweeping");
		assertEquals(sweeping, EnchantmentRegistry.forProtocol(316).keyById(22));
		assertEquals(sweeping, EnchantmentRegistry.forProtocol(765).keyById(19));
		assertEquals(MinecraftKey.withDefaultNamespace("sweeping_edge"), EnchantmentRegistry.forProtocol(766).keyById(19));
		assertEquals(-1, EnchantmentRegistry.forProtocol(766).idOf(sweeping));
		assertEquals(13, EnchantmentRegistry.forProtocol(766).idOf(SHARPNESS));
		assertEquals(32, EnchantmentRegistry.forProtocol(767).idOf(SHARPNESS));
		MinecraftKey lunge = MinecraftKey.withDefaultNamespace("lunge");
		assertEquals(-1, EnchantmentRegistry.forProtocol(773).idOf(lunge));
		assertEquals(lunge, EnchantmentRegistry.forProtocol(774).keyById(21));
		assertEquals(33, EnchantmentRegistry.forProtocol(777).idOf(SHARPNESS));
	}

	@Test
	void synchronizedRegistriesKeepEachConnectionsOrderAndCustomKeys() {
		MinecraftKey custom = new MinecraftKey("example", "sharpness");
		List<MinecraftKey> entries = new ArrayList<>(Arrays.asList(custom, SHARPNESS, PROTECTION));
		EnchantmentRegistry first = EnchantmentRegistry.fromNetworkOrder(entries);
		entries.clear();
		EnchantmentRegistry second = EnchantmentRegistry.fromNetworkOrder(Arrays.asList(PROTECTION, custom));
		assertEquals(custom, first.keyById(0));
		assertEquals(1, first.idOf(SHARPNESS));
		assertEquals(2, first.idOf(PROTECTION));
		assertEquals(0, second.idOf(PROTECTION));
		assertEquals(1, second.idOf(custom));
		assertEquals(-1, second.idOf(SHARPNESS));
		assertEquals(-1, EnchantmentRegistry.forProtocol(777).idOf(custom));
		assertThrows(UnsupportedOperationException.class, () -> first.enchantmentIds().clear());
		assertThrows(IllegalArgumentException.class, () -> EnchantmentRegistry.fromNetworkOrder(Arrays.asList(custom, custom)));
	}
}

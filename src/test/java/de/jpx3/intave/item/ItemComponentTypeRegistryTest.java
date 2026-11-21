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

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

final class ItemComponentTypeRegistryTest {
	@Test
	void everyComponentProtocolHasAnExactBidirectionalTable() {
		Set<Integer> expected = ItemRegistry.supportedProtocolVersions().stream()
			.filter(protocol -> protocol >= 766)
			.collect(Collectors.toSet());
		assertEquals(expected, ItemComponentTypeRegistry.supportedProtocolVersions());
		for (int protocol : expected) {
			ItemComponentTypeRegistry registry = ItemComponentTypeRegistry.forProtocol(protocol);
			for (int id = 0; id < registry.componentTypeIds().size(); id++) {
				MinecraftKey key = registry.keyById(id);
				assertNotNull(key);
				assertEquals(id, registry.idOf(key));
			}
			assertNull(registry.keyById(-1));
			assertNull(registry.keyById(registry.componentTypeIds().size()));
			assertEquals(-1, registry.idOf(new MinecraftKey("example", "damage")));
		}
		assertThrows(IllegalArgumentException.class, () -> ItemComponentTypeRegistry.forProtocol(765));
		assertThrows(IllegalArgumentException.class, () -> ItemComponentTypeRegistry.forProtocol(Integer.MAX_VALUE));
	}

	@Test
	void profileMovesAtVanillaRegistryBoundaries() {
		MinecraftKey profile = MinecraftKey.withDefaultNamespace("profile");
		int[] profileIds = {46, 47, 57, 57, 61, 61, 61, 61, 68, 70, 70, 72};
		for (int offset = 0; offset < profileIds.length; offset++) {
			int protocol = 766 + offset;
			assertEquals(profileIds[offset], ItemComponentTypeRegistry.forProtocol(protocol).idOf(profile), "protocol " + protocol);
		}

		MinecraftKey cushionColor = MinecraftKey.withDefaultNamespace("cushion/color");
		assertEquals(-1, ItemComponentTypeRegistry.forProtocol(776).idOf(cushionColor));
		assertEquals(cushionColor, ItemComponentTypeRegistry.forProtocol(777).keyById(121));
	}
}

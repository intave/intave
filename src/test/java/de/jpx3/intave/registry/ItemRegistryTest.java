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

package de.jpx3.intave.registry;

import de.jpx3.intave.item.ItemRegistry;
import de.jpx3.intave.share.MinecraftKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ItemRegistryTest {
	@Test
	void everyWireProtocolHasABidirectionalVanillaRegistry() {
		assertEquals(PacketTypeRegistries.clientbound().supportedProtocolVersions(), ItemRegistry.supportedProtocolVersions());
		for (int protocol : ItemRegistry.supportedProtocolVersions()) {
			ItemRegistry items = ItemRegistry.forProtocol(protocol);
			assertEquals(MinecraftKey.withDefaultNamespace("stone"), items.keyById(1));
			items.itemIds().forEach((key, id) -> {
				assertEquals(key, items.keyById(id), "item ID " + id + " in " + protocol);
				assertEquals(id.intValue(), items.idOf(key));
			});
			assertNull(items.keyById(-1));
			assertNull(items.keyById(Integer.MAX_VALUE));
			assertEquals(-1, items.idOf(new MinecraftKey("example", "stone")));
		}
		assertThrows(IllegalArgumentException.class, () -> ItemRegistry.forProtocol(48));
	}

	@Test
	void legacyIdsKeepTheirGapsAndOriginalNames() {
		ItemRegistry oldest = ItemRegistry.forProtocol(47);
		assertNull(oldest.keyById(0));
		assertEquals(MinecraftKey.withDefaultNamespace("lit_furnace"), oldest.keyById(62));
		assertEquals(2267, oldest.idOf(MinecraftKey.withDefaultNamespace("record_wait")));
		assertNull(oldest.keyById(2268));
		assertNull(ItemRegistry.forProtocol(107).keyById(62));

		ItemRegistry first = ItemRegistry.forProtocol(315);
		MinecraftKey totem = MinecraftKey.withDefaultNamespace("totem");
		assertEquals(totem, first.keyById(449));
		assertNull(first.keyById(452));
		ItemRegistry next = ItemRegistry.forProtocol(316);
		assertEquals(-1, next.idOf(totem));
		assertEquals(MinecraftKey.withDefaultNamespace("totem_of_undying"), next.keyById(449));
		assertEquals(MinecraftKey.withDefaultNamespace("iron_nugget"), next.keyById(452));
	}

	@Test
	void flattenedIdsFollowExactProtocolBoundaries() {
		MinecraftKey totem = MinecraftKey.withDefaultNamespace("totem_of_undying");
		assertEquals(764, ItemRegistry.forProtocol(393).idOf(totem));
		assertEquals(769, ItemRegistry.forProtocol(401).idOf(totem));
		assertSame(ItemRegistry.forProtocol(401), ItemRegistry.forProtocol(404));
		assertEquals(1212, ItemRegistry.forProtocol(762).idOf(MinecraftKey.withDefaultNamespace("netherite_upgrade_smithing_template")));
		assertEquals(1163, ItemRegistry.forProtocol(766).idOf(totem));
		assertEquals(1454, ItemRegistry.forProtocol(777).idOf(totem));
		assertEquals(1658, ItemRegistry.forProtocol(777).itemIds().size());
	}
}

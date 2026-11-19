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

import de.jpx3.intave.block.state.BlockState;
import de.jpx3.intave.block.state.BlockStates;
import de.jpx3.intave.packet.wire.WirePacketTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static org.junit.jupiter.api.Assertions.*;

final class BlockCacheEntryRegistryTest {
	@Test
	void coversEverySupportedWireProtocolAndRejectsUnknownProtocols() {
		for (int protocol : WirePacketTestSupport.registry(CLIENTBOUND).supportedProtocolVersions()) {
			assertTrue(BlockStates.supportedProtocolVersions().contains(protocol), "Protocol " + protocol);
			assertEquals("minecraft:air", BlockStates.stateOf(protocol, 0).name());
		}
		assertThrows(IllegalArgumentException.class, () -> BlockStates.stateOf(9999, 0));
		assertThrows(IllegalArgumentException.class, () -> BlockStates.stateOf(769, -1));
		assertThrows(IllegalArgumentException.class, () -> BlockStates.stateOf(769, 27866));
		assertThrows(IllegalArgumentException.class, () -> BlockStates.stateOf(47, 4096));
		assertThrows(UnsupportedOperationException.class, () -> BlockStates.supportedProtocolVersions().clear());
	}

	@Test
	void usesActualStateOrderingAcrossFlatteningAndRegistryChanges() {
		int[] protocols = {393, 404, 735, 754, 765, 769, 777};
		int[] stairs = {1719, 1720, 2025, 2025, 2945, 3000, 5534};
		int[] logs = {72, 72, 73, 73, 130, 136, 139};
		for (int i = 0; i < protocols.length; i++) {
			BlockState state = BlockStates.stateOf(protocols[i], stairs[i]);
			assertEquals("minecraft:oak_stairs", state.name());
			assertEquals(Map.of("facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false"), state.properties());
			assertEquals(3000, BlockStates.translate(protocols[i], stairs[i], 769).id());
			assertEquals("x", BlockStates.stateOf(protocols[i], logs[i]).properties().get("axis"));
			assertEquals(136, BlockStates.translate(protocols[i], logs[i], 769).id());
		}
	}

	@Test
	void resolvesRangeBoundariesWithoutLeakingIntoAdjacentBlocks() {
		assertEquals("minecraft:air", BlockStates.stateOf(769, 0).name());
		assertEquals("minecraft:stone", BlockStates.stateOf(769, 1).name());
		assertEquals("minecraft:oak_log[axis=x]", BlockStates.stateOf(769, 136).toString());
		assertEquals("minecraft:oak_log[axis=z]", BlockStates.stateOf(769, 138).toString());
		assertEquals("minecraft:spruce_log[axis=x]", BlockStates.stateOf(769, 139).toString());
		assertEquals("minecraft:oak_stairs[facing=north,half=top,shape=straight,waterlogged=true]",
			BlockStates.stateOf(769, 2929).toString());
		assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=outer_right,waterlogged=false]",
			BlockStates.stateOf(769, 3008).toString());
		assertEquals("minecraft:potted_closed_eyeblossom", BlockStates.stateOf(769, 27865).name());
		assertThrows(IllegalArgumentException.class, () -> BlockStates.stateOf(769, Integer.MAX_VALUE));
	}

	@Test
	void legacyMetadataResolvesThroughFlatteningAndTargetDefaults() {
		assertEquals("minecraft:granite", BlockStates.stateOf(47, 17).name());
		assertEquals(2, BlockStates.translate(47, 17, 769).id());
		assertEquals("minecraft:lime_wool", BlockStates.stateOf(340, (35 << 4) | 5).name());
		BlockState stairs = BlockStates.translate(47, 53 << 4, 769);
		assertEquals(3000, stairs.id());
		assertEquals("false", stairs.properties().get("waterlogged"));
		assertEquals(53 << 4, BlockStates.translate(769, 3000, 47).id());
		assertEquals(0, BlockStates.stateOf(340, 2114).legacyVariantId()); // removed suspended bit
		assertEquals(2, BlockStates.stateOf(47, 2114).legacyVariantId());
		assertEquals("true", BlockStates.stateOf(47, 2127).properties().get("powered"));
		assertThrows(IllegalArgumentException.class, () -> BlockStates.stateOf(47, 17 << 8));
	}

	@Test
	void appliesBlockRenamesSplitsAndPropertyChangesInBothDirections() {
		assertEquals(13526, BlockStates.translate(404, 8163, 769).id());
		assertEquals(8163, BlockStates.translate(769, 13526, 404).id());
		assertEquals(2048, BlockStates.translate(404, 1041, 769).id());
		assertEquals(12113, BlockStates.translate(404, 7294, 769).id());
		assertEquals(7294, BlockStates.translate(769, 12113, 404).id());
		assertEquals(8172, BlockStates.translate(404, 4622, 769).id());
		assertEquals(4622, BlockStates.translate(769, 8172, 404).id());
		assertEquals(8175, BlockStates.translate(404, 4625, 769).id());
		assertEquals(4625, BlockStates.translate(769, 8175, 404).id());
		BlockState wall = BlockStates.translate(47, 139 << 4, 769);
		assertEquals("minecraft:cobblestone_wall", wall.name());
		assertEquals("none", wall.properties().get("north"));
		assertEquals("false", wall.properties().get("waterlogged"));
	}

	@Test
	void sharedRegistriesKeepTheTwoStoneSlabsDistinct() {
		// 1.13 and 1.13.1 share one registry. The 1.14 releases share another.
		for (int oldProtocol : new int[]{393, 401}) {
			for (int newProtocol : new int[]{477, 498}) {
				assertEquals(7809, BlockStates.translate(oldProtocol, 7296, newProtocol).id());
				assertEquals(7296, BlockStates.translate(newProtocol, 7809, oldProtocol).id());
				assertThrows(UnsupportedOperationException.class,
					() -> BlockStates.translate(newProtocol, 7803, oldProtocol));
			}
		}
		assertEquals(7803, BlockStates.translate(477, 7803, 498).id());
	}

	@Test
	void rejectsUnrepresentableBlocksAndPropertiesInsteadOfSubstitutingAir() {
		// Waterlogged stairs have no legacy representation.
		assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(769, 2999, 47));
		// Mangrove logs did not exist in 1.13.
		int mangroveLog = findByName(769, "minecraft:mangrove_log");
		assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(769, mangroveLog, 404));
		assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(777, 35722, 47));
	}

	@Test
	void registryStatesAreImmutableAndSameVersionTranslationPreservesTheExactId() {
		BlockState state = BlockStates.stateOf(769, 3000);
		assertThrows(UnsupportedOperationException.class, () -> state.properties().put("facing", "west"));
		assertEquals(3000, BlockStates.translate(769, 3000, 769).id());
		assertEquals(2114, BlockStates.translate(340, 2114, 340).id());
		assertEquals("minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]", state.toString());
	}

	private static int findByName(int protocol, String name) {
		for (int id = 0; id < 27866; id++) if (BlockStates.stateOf(protocol, id).name().equals(name)) return id;
		throw new AssertionError("Missing fixture block " + name);
	}
}


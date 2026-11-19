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

import com.google.gson.JsonParser;
import de.jpx3.intave.resource.Resources;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.jpx3.intave.codec.JsonStreamCodecs.decodeFromString;
import static de.jpx3.intave.codec.JsonStreamCodecs.encodeToTree;
import static org.junit.jupiter.api.Assertions.*;

final class BlockStatesTest {
	@Test
	void protocolsShareTheInstanceForTheirRegistryFile() {
		assertSame(BlockStates.registryFor(393), BlockStates.registryFor(401)); // both use 1.13.json
		assertNotSame(BlockStates.registryFor(401), BlockStates.registryFor(404)); // 1.13.2.json
	}

	@Test
	void connectedFenceResolvesToLegacyMetadata() {
		BlockState source = BlockStates.stateOf(774, 6787);
		assertEquals("minecraft:oak_fence[east=false,north=true,south=false,waterlogged=false,west=false]", source.toString());
		BlockState legacy = BlockStates.translate(774, source.id(), 47);
		assertEquals(85 << 4, legacy.id());
		assertEquals(0, legacy.legacyVariantId());
		assertEquals("true", source.properties().get("north"));
		assertEquals(source.properties(), BlockStates.translate(774, source.id(), 393).properties());
	}

	@Test
	void allLegacyFenceTypesAndConnectionsResolveWithoutChangingTheirIdentity() {
		// BlockFence.getMetaFromState returns zero; getActualState supplies the connections.
		for (int blockId : new int[]{85, 113, 188, 189, 190, 191, 192}) {
			String name = BlockStates.stateOf(47, blockId << 4).name();
			for (int sourceProtocol : new int[]{393, 774}) {
				for (int mask = 0; mask < 16; mask++) {
					Map<String, String> properties = Map.of(
						"north", Boolean.toString((mask & 1) != 0),
						"east", Boolean.toString((mask & 2) != 0),
						"south", Boolean.toString((mask & 4) != 0),
						"west", Boolean.toString((mask & 8) != 0),
						"waterlogged", "false"
					);
					BlockState source = BlockStates.registryFor(sourceProtocol).find(name, properties);
					assertNotNull(source, name);
					for (int targetProtocol : new int[]{47, 110, 210, 316, 340}) {
						BlockState target = BlockStates.translate(sourceProtocol, source.id(), targetProtocol);
						assertEquals(blockId << 4, target.id(), source + " -> " + targetProtocol);
						assertEquals(0, target.legacyVariantId());
					}
				}
			}
		}
	}

	@Test
	void legacyFenceConversionStillRejectsUnsupportedStates() {
		BlockState wetFence = BlockStates.registryFor(774).find("minecraft:oak_fence", Map.of("north", "true", "waterlogged", "true"));
		BlockState newFence = BlockStates.registryFor(774).find("minecraft:mangrove_fence", Map.of("north", "true"));
		assertNotNull(wetFence);
		assertNotNull(newFence);
		for (int targetProtocol : new int[]{47, 340}) {
			assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(774, wetFence.id(), targetProtocol));
			assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(774, newFence.id(), targetProtocol));
			assertNull(BlockStates.registryFor(targetProtocol).find("minecraft:oak_fence", Map.of("unknown", "false")));
		}
	}

	@Test
	void upperLilacResolvesToTheSharedLegacyDoublePlantState() {
		BlockState source = BlockStates.stateOf(774, 12715);
		assertEquals("minecraft:lilac[half=upper]", source.toString());
		BlockState legacy = BlockStates.translate(774, source.id(), 47);
		assertEquals((175 << 4) | 8, legacy.id());
		assertEquals(8, legacy.legacyVariantId());
		assertEquals("upper", legacy.properties().get("half"));
		assertEquals("minecraft:lilac", source.name());
	}

	@Test
	void doublePlantsShareOnlyTheirLegacyUpperHalf() {
		String[] plants = {"sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"};
		for (int sourceProtocol : new int[]{393, 774}) {
			for (int variant = 0; variant < plants.length; variant++) {
				for (String half : new String[]{"upper", "lower"}) {
					String name = "minecraft:" + plants[variant];
					BlockState source = BlockStates.registryFor(sourceProtocol).find(name, Map.of("half", half));
					assertNotNull(source, name);
					for (int targetProtocol : new int[]{47, 110, 210, 316, 340}) {
						int metadata = half.equals("upper") ? 8 : variant;
						BlockState legacy = BlockStates.translate(sourceProtocol, source.id(), targetProtocol);
						assertEquals((175 << 4) | metadata, legacy.id(), source + " -> " + targetProtocol);
						assertEquals(metadata, legacy.legacyVariantId());
						assertEquals(half, legacy.properties().get("half"));
					}
					BlockState modern = BlockStates.translate(sourceProtocol, source.id(), sourceProtocol == 393 ? 774 : 393);
					assertEquals(source.name(), modern.name());
					assertEquals(source.properties(), modern.properties());
				}
			}
		}
		for (int metadata = 8; metadata <= 11; metadata++) {
			int id = (175 << 4) | metadata;
			assertEquals(id, BlockStates.translate(47, id, 340).id());
			assertEquals(id, BlockStates.translate(340, id, 47).id());
		}
	}

	@Test
	void newDoublePlantsStillHaveNoLegacyRepresentation() {
		for (String half : new String[]{"upper", "lower"}) {
			BlockState source = BlockStates.registryFor(774).find("minecraft:pitcher_plant", Map.of("half", half));
			assertNotNull(source);
			assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(774, source.id(), 47));
			assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(774, source.id(), 340));
		}
	}

	@Test
	void roundTripsBundledLegacyAndModernTables() {
		for (String version : new String[]{"1.8.8", "1.13.2", "1.21.4"}) {
			String json = Resources.resourceFromJarOrBuild("registry/block_states/" + version + ".json").readAsString();
			BlockStates registry = decodeFromString(BlockStates.JSON_CODEC, json);
			assertEquals(new JsonParser().parse(json), encodeToTree(BlockStates.JSON_CODEC, registry), version);
		}
	}

	@Test
	void appliesOnlySuppliedPropertiesToTheNativeDefault() {
		String json = "{\"blocks\":[[0,\"minecraft:test\",[[\"z\",[\"x\",\"y\",\"z\"]],[\"a\",[\"true\",\"false\"]]],3]],"
			+ "\"legacy\":false,\"schemaVersion\":1}";
		BlockStates registry = decodeFromString(BlockStates.JSON_CODEC, json);
		assertEquals(3, registry.find("minecraft:test", Map.of()).id());
		assertEquals(5, registry.find("minecraft:test", Map.of("z", "z")).id());
		assertEquals(2, registry.find("minecraft:test", Map.of("a", "true")).id());
		assertEquals(0, registry.find("minecraft:test", Map.of("a", "true", "z", "x")).id());
		assertEquals(3, registry.find("minecraft:test", Map.of("waterlogged", "false")).id());
		assertNull(registry.find("minecraft:test", Map.of("waterlogged", "true")));
		assertNull(registry.find("minecraft:test", Map.of("unknown", "false")));
		assertNull(registry.find("minecraft:test", Map.of("z", "other")));
	}

	@Test
	void keepsCanonicalCauldronDefaultsSeparate() {
		String json = "{\"schemaVersion\":1,\"legacy\":false,\"blocks\":[[0,\"minecraft:cauldron\","
			+ "[[\"level\",[\"0\",\"1\",\"2\",\"3\"]]],0]]}";
		BlockStates registry = decodeFromString(BlockStates.JSON_CODEC, json);
		assertEquals(0, registry.find("minecraft:cauldron", Map.of()).id());
		assertEquals(1, registry.find("minecraft:water_cauldron", Map.of()).id());
		assertNull(registry.find("minecraft:cauldron", Map.of("level", "1")));
		assertNull(registry.find("minecraft:water_cauldron", Map.of("level", "0")));
	}
}

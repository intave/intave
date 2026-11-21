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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.jpx3.intave.resource.Resources;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class LegacyTranslationAuditTest {
	private static final int SOURCE_PROTOCOL = 774;
	private static final Map<String, Set<Integer>> BLOCK_IDS = blockIds();
	private static final Map<Integer, Map<String, Set<Integer>>> ADDITIONAL_BLOCK_IDS = additionalBlockIds();
	private static final List<Integer> LEGACY_PROTOCOLS = BlockStates.supportedProtocolVersions().stream()
		.filter(protocol -> protocol < 393).sorted().toList();
	private static final Map<String, Integer> FACING = Map.of("down", 0, "up", 1, "north", 2, "south", 3, "west", 4, "east", 5);
	private static final Map<String, Integer> HORIZONTAL = Map.of("south", 0, "west", 1, "north", 2, "east", 3);
	private static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak");
	private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

	@Test
	void every774StateHasAnExplicitOutcomeForEveryLegacyProtocol() throws Exception {
		int total = ((Number) Index.get().sources().get("1.21.11").get("states")).intValue();
		assertEquals(29671, total, "Review the audit when the bundled source registry changes");
		List<String> summary = new ArrayList<>();
		summary.add("Protocol 774 -> " + LEGACY_PROTOCOLS.size() + " legacy protocols: " + total * LEGACY_PROTOCOLS.size() + " state/protocol pairs audited");
		Map<String, Integer> failures = new TreeMap<>();
		Map<String, String> examples = new TreeMap<>();
		Path directory = Path.of("build", "reports", "legacy-translation-audit");
		Files.createDirectories(directory);
		for (int protocol : LEGACY_PROTOCOLS) {
			Map<String, Set<Integer>> blockIds = blockIds(protocol);
			List<BlockState> legacyStates = legacyStates(protocol);
			Set<String> unvisited = new TreeSet<>(blockIds.keySet());
			Map<String, Integer> outcomes = new TreeMap<>();
			Map<String, Integer> translatedByBlock = new TreeMap<>();
			for (int id = 0; id < total; id++) {
				BlockState source = BlockStates.stateOf(SOURCE_PROTOCOL, id);
				String name = source.nameKey().path();
				unvisited.remove(name);
				String reason = unsupportedReason(source, legacyStates, protocol, blockIds);
				try {
					if (reason != null) {
						assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(SOURCE_PROTOCOL, source.id(), protocol), source::toString);
						outcomes.merge(reason, 1, Integer::sum);
						continue;
					}
					BlockState target = assertDoesNotThrow(() -> BlockStates.translate(SOURCE_PROTOCOL, source.id(), protocol), source::toString);
					assertTrue(blockIds.get(name).contains(target.id() >>> 4), () -> source + " mapped to unrelated legacy block " + target.id());
					Integer metadata = nativeMetadata(source, protocol);
					if (metadata != null) {
						assertEquals(metadata.intValue(), target.id() & 15, source::toString);
						assertEquals(metadata.intValue(), target.legacyVariantId(), source::toString);
					}
					if (name.equals("purpur_slab")) {
						assertEquals(source.properties().get("type").equals("double") ? 204 : 205, target.id() >>> 4);
					}
					outcomes.merge("translated", 1, Integer::sum);
					translatedByBlock.merge(name, 1, Integer::sum);
				} catch (AssertionError failure) {
					String key = protocol + " " + name;
					failures.merge(key, 1, Integer::sum);
					examples.putIfAbsent(key, failure.getMessage());
					outcomes.merge("unexpected", 1, Integer::sum);
				}
			}
			assertTrue(unvisited.isEmpty(), () -> "Stale fixture names: " + unvisited);
			String header = "Protocol " + protocol + " (" + Index.get().protocols().get(Integer.toString(protocol)) + "): " + outcomes;
			summary.add(header);
			List<String> details = new ArrayList<>();
			details.add(header);
			translatedByBlock.forEach((name, count) -> details.add(name + ": " + count));
			Files.write(directory.resolve(protocol + ".txt"), details);
		}
		failures.forEach((key, count) -> summary.add("FAILED " + key + " (" + count + " states): " + examples.get(key)));
		Files.write(Path.of("build", "reports", "legacy-translation-audit.txt"), summary);
		assertTrue(failures.isEmpty(), () -> String.join("\n", summary));
	}

	@Test
	void modernTargetsRetainNeighborAndBlockEntityProperties() {
		Map<String, Set<Integer>> blockIds = blockIds(340);
		int total = ((Number) Index.get().sources().get("1.21.11").get("states")).intValue();
		for (int id = 0; id < total; id++) {
			BlockState source = BlockStates.stateOf(SOURCE_PROTOCOL, id);
			if (!blockIds.containsKey(source.nameKey().path())) continue;
			BlockState exact = BlockStates.registryFor(773).find(source.name(), source.properties());
			if (exact != null) {
				BlockState translated = BlockStates.translate(SOURCE_PROTOCOL, id, 773);
				assertEquals(exact.id(), translated.id(), source::toString);
				assertEquals(source.name(), translated.name());
				assertEquals(source.properties(), translated.properties());
			}
		}
	}

	@Test
	void commandBlocksAndSkullsRespectThe19Boundary() {
		for (String facing : FACING.keySet()) {
			for (boolean conditional : new boolean[]{false, true}) {
				BlockState source = BlockStates.registryFor(774).find("minecraft:command_block",
					Map.of("facing", facing, "conditional", Boolean.toString(conditional)));
				assertNotNull(source);
				BlockState in19 = BlockStates.translate(774, source.id(), 110);
				assertEquals((137 << 4) | FACING.get(facing) | (conditional ? 8 : 0), in19.id());
			}
		}
		BlockState dragon = BlockStates.registryFor(774).find("minecraft:dragon_head", Map.of("rotation", "13", "powered", "true"));
		assertNotNull(dragon);
		assertThrows(UnsupportedOperationException.class, () -> BlockStates.translate(774, dragon.id(), 47));
		assertEquals((144 << 4) | 1, BlockStates.translate(774, dragon.id(), 110).id());
	}

	@Test
	void legacyProjectionDoesNotIgnoreUnknownProperties() {
		for (String name : List.of("oak_stairs", "oak_door", "redstone_wire", "note_block", "oak_wood", "oak_leaves")) {
			BlockState source = BlockStates.registryFor(774).find("minecraft:" + name, Map.of());
			assertNotNull(source);
			Map<String, String> properties = new HashMap<>(source.properties());
			properties.put("unknown", "false");
			assertNull(BlockStates.registryFor(47).find("minecraft:" + name, properties), name);
		}
	}

	private static String unsupportedReason(BlockState state, List<BlockState> legacyStates, int protocol, Map<String, Set<Integer>> blockIds) {
		String name = state.nameKey().path();
		if (!blockIds.containsKey(name)) return "block absent in target";
		if (flag(state, "waterlogged")) return "waterlogged";
		if (protocol == 47 && name.equals("command_block") && flag(state, "conditional")) return "conditional command block";
		if (name.equals("brown_mushroom_block") || name.equals("red_mushroom_block") || name.equals("mushroom_stem")) {
			// BlockHugeMushroom has 13 fixed texture variants, not six independent faces.
			boolean supported = legacyStates.stream().anyMatch(old -> old.name().equals(state.name()) && old.properties().equals(state.properties()));
			if (!supported) return "mushroom faces without legacy metadata";
		}
		return null;
	}

	/** Independent metadata oracle from client/1.8.9 through 1.12.2 Block*.getMetaFromState. */
	private static Integer nativeMetadata(BlockState state, int protocol) {
		String name = state.nameKey().path();
		Map<String, String> p = state.properties();
		if (name.endsWith("_stairs")) return Map.of("east", 0, "west", 1, "south", 2, "north", 3).get(p.get("facing")) | (p.get("half").equals("top") ? 4 : 0);
		if (name.endsWith("_door")) {
			if (p.get("half").equals("upper")) return 8 | (p.get("hinge").equals("right") ? 1 : 0) | (flag(state, "powered") ? 2 : 0);
			return Map.of("east", 0, "south", 1, "west", 2, "north", 3).get(p.get("facing")) | (flag(state, "open") ? 4 : 0);
		}
		if (name.endsWith("_trapdoor")) return Map.of("north", 0, "south", 1, "west", 2, "east", 3).get(p.get("facing")) | (flag(state, "open") ? 4 : 0) | (p.get("half").equals("top") ? 8 : 0);
		if (name.endsWith("_fence_gate")) return HORIZONTAL.get(p.get("facing")) | (flag(state, "open") ? 4 : 0) | (flag(state, "powered") ? 8 : 0);
		if (name.endsWith("_fence")) return 0;
		if (name.endsWith("_stained_glass_pane")) return COLORS.indexOf(name.substring(0, name.length() - "_stained_glass_pane".length()));
		if (name.equals("glass_pane") || name.equals("iron_bars") || name.equals("cobblestone_wall")) return 0;
		if (name.equals("mossy_cobblestone_wall")) return 1;
		if (name.equals("chest") || name.equals("trapped_chest")) return FACING.get(p.get("facing"));
		if (name.equals("redstone_wire")) return Integer.parseInt(p.get("power"));
		if (name.equals("fire")) return Integer.parseInt(p.get("age"));
		if (name.equals("tripwire")) return (flag(state, "powered") ? 1 : 0) | (flag(state, "attached") ? 4 : 0) | (flag(state, "disarmed") ? 8 : 0);
		if (name.equals("vine")) return (flag(state, "south") ? 1 : 0) | (flag(state, "west") ? 2 : 0) | (flag(state, "north") ? 4 : 0) | (flag(state, "east") ? 8 : 0);
		if (name.equals("repeater")) return HORIZONTAL.get(p.get("facing")) | (Integer.parseInt(p.get("delay")) - 1) << 2;
		if (name.equals("piston_head")) return FACING.get(p.get("facing")) | (p.get("type").equals("sticky") ? 8 : 0);
		if (name.equals("grass_block") || name.equals("mycelium")) return 0;
		if (name.equals("podzol")) return 2;
		if (name.equals("note_block")) return 0;
		if (name.equals("command_block") || name.endsWith("_command_block"))
			return protocol == 47 ? 0 : FACING.get(p.get("facing")) | (flag(state, "conditional") ? 8 : 0);
		if (name.endsWith("_leaves")) return WOODS.indexOf(name.substring(0, name.length() - 7)) % 4 | (flag(state, "persistent") ? 4 : 0);
		if (name.endsWith("_wood")) return 12 | WOODS.indexOf(name.substring(0, name.length() - 5)) % 4;
		if (name.endsWith("_bed")) return HORIZONTAL.get(p.get("facing")) | (p.get("part").equals("head") ? 8 | (flag(state, "occupied") ? 4 : 0) : 0);
		if (name.endsWith("_button") || name.equals("lever")) {
			int direction;
			if (p.get("face").equals("wall")) direction = Map.of("east", 1, "west", 2, "south", 3, "north", 4).get(p.get("facing"));
			else if (name.endsWith("_button")) direction = p.get("face").equals("floor") ? 5 : 0;
			else {
				boolean x = p.get("facing").equals("east") || p.get("facing").equals("west");
				direction = p.get("face").equals("floor") ? (x ? 6 : 5) : (x ? 0 : 7);
			}
			return direction | (flag(state, "powered") ? 8 : 0);
		}
		if (name.equals("flower_pot") || name.startsWith("potted_")) return 0;
		if (name.endsWith("_wall_banner")) return FACING.get(p.get("facing"));
		if (name.endsWith("_banner")) return Integer.parseInt(p.get("rotation"));
		if (name.endsWith("_skull") || name.endsWith("_head")) return p.containsKey("rotation") ? 1 : FACING.get(p.get("facing"));
		if (name.startsWith("attached_")) return 7;
		List<String> plants = List.of("sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony");
		if (plants.contains(name)) return p.get("half").equals("upper") ? 8 : plants.indexOf(name);
		if (name.equals("melon") || name.equals("spawner")) return 0;
		if (name.equals("nether_portal")) return p.get("axis").equals("x") ? 1 : 2;
		if (name.equals("chorus_plant") || name.equals("purpur_block") || name.equals("end_gateway")
			|| name.equals("end_stone_bricks") || name.equals("dirt_path") || name.equals("magma_block")
			|| name.equals("nether_wart_block") || name.equals("red_nether_bricks") || name.equals("structure_void")) return 0;
		if (name.equals("chorus_flower") || name.equals("beetroots") || name.equals("frosted_ice")) return Integer.parseInt(p.get("age"));
		if (name.equals("end_rod") || name.endsWith("_shulker_box")) return FACING.get(p.get("facing"));
		if (name.equals("purpur_pillar") || name.equals("bone_block")) return Map.of("x", 4, "y", 0, "z", 8).get(p.get("axis"));
		if (name.equals("purpur_slab")) return p.get("type").equals("top") ? 8 : 0;
		if (name.equals("structure_block")) return Map.of("save", 0, "load", 1, "corner", 2, "data", 3).get(p.get("mode"));
		if (name.equals("observer")) return FACING.get(p.get("facing")) | (flag(state, "powered") ? 8 : 0);
		if (name.endsWith("_glazed_terracotta")) return HORIZONTAL.get(p.get("facing"));
		if (name.endsWith("_concrete")) return COLORS.indexOf(name.substring(0, name.length() - "_concrete".length()));
		if (name.endsWith("_concrete_powder")) return COLORS.indexOf(name.substring(0, name.length() - "_concrete_powder".length()));
		return null;
	}

	private static boolean flag(BlockState state, String key) {
		return "true".equals(state.properties().get(key));
	}

	private static List<BlockState> legacyStates(int protocol) {
		List<BlockState> states = new ArrayList<>();
		for (int id = 0; id < 4096; id++) {
			try { states.add(BlockStates.stateOf(protocol, id)); }
			catch (IllegalArgumentException ignored) {}
		}
		return states;
	}

	private static Map<String, Set<Integer>> blockIds() {
		String json = Resources.resourceFromJarOrBuild("registry/protocol_774_to_47_blocks.json").readAsString();
		return blockIds(new JsonParser().parse(json));
	}

	private static Map<String, Set<Integer>> blockIds(int protocol) {
		Map<String, Set<Integer>> result = new TreeMap<>(BLOCK_IDS);
		ADDITIONAL_BLOCK_IDS.forEach((introduced, additions) -> {
			if (protocol >= introduced) result.putAll(additions);
		});
		return result;
	}

	private static Map<Integer, Map<String, Set<Integer>>> additionalBlockIds() {
		String json = Resources.resourceFromJarOrBuild("registry/protocol_774_legacy_additions.json").readAsString();
		Map<Integer, Map<String, Set<Integer>>> result = new TreeMap<>();
		for (Map.Entry<String, JsonElement> entry : new JsonParser().parse(json).getAsJsonObject().entrySet()) {
			result.put(Integer.parseInt(entry.getKey()), blockIds(entry.getValue()));
		}
		return result;
	}

	private static Map<String, Set<Integer>> blockIds(JsonElement json) {
		Map<String, Set<Integer>> result = new TreeMap<>();
		for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
			Set<Integer> ids = new HashSet<>();
			for (JsonElement id : entry.getValue().getAsJsonArray()) ids.add(id.getAsInt());
			result.put(entry.getKey(), ids);
		}
		return result;
	}
}

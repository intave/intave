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

import java.util.Map;
import java.util.TreeMap;

/** Block schema changes and legacy metadata projection, independent of registry numbering. */
final class LegacyProperties {
	private LegacyProperties() {}

	/** Returns the lookup name in the target registry, or null if that block did not exist. */
	static String nameForTarget(BlockState state, int sourceProtocol, int targetProtocol) {
		String name = name(state);
		if (targetProtocol < 393) {
			if (name.equals("minecraft:attached_melon_stem")) return "minecraft:melon_stem";
			if (name.equals("minecraft:attached_pumpkin_stem")) return "minecraft:pumpkin_stem";
			// Banner color, pot contents and skull type live in legacy block entities.
			if (name.endsWith("_wall_banner")) return "minecraft:white_wall_banner";
			if (name.endsWith("_banner")) return "minecraft:white_banner";
			// 1.12 added bed colors in TileEntityBed; the metadata still uses block 26.
			if (targetProtocol >= 335 && name.endsWith("_bed")) return "minecraft:red_bed";
			if (legacyFlowerPot(name)) return "minecraft:potted_cactus";
			if (legacySkull(name, targetProtocol)) return "minecraft:skull";
		}
		if (targetProtocol < 393 && "upper".equals(state.properties().get("half"))) {
			// Legacy double_plant upper halves get their species from the block below.
			// Vanilla's flattening table labels their shared metadata states as peony.
			switch (name) {
				case "minecraft:sunflower":
				case "minecraft:lilac":
				case "minecraft:tall_grass":
				case "minecraft:large_fern":
				case "minecraft:rose_bush":
				case "minecraft:peony":
					return "minecraft:peony";
			}
		}
		// 1.14 renamed the original slab and reused stone_slab for a new block.
		if (sourceProtocol < 477 && targetProtocol >= 477 && name.equals("minecraft:stone_slab")) {
			return "minecraft:smooth_stone_slab";
		}
		if (sourceProtocol >= 477 && targetProtocol < 477) {
			if (name.equals("minecraft:smooth_stone_slab")) return "minecraft:stone_slab";
			if (name.equals("minecraft:stone_slab")) return null;
		}
		return name;
	}

	static String name(BlockState state) {
		String name = state.name();
		switch (name) {
			case "minecraft:melon_block": return "minecraft:melon";
			case "minecraft:mob_spawner": return "minecraft:spawner";
			case "minecraft:portal": return "minecraft:nether_portal";
			case "minecraft:oak_bark": return "minecraft:oak_wood";
			case "minecraft:spruce_bark": return "minecraft:spruce_wood";
			case "minecraft:birch_bark": return "minecraft:birch_wood";
			case "minecraft:jungle_bark": return "minecraft:jungle_wood";
			case "minecraft:acacia_bark": return "minecraft:acacia_wood";
			case "minecraft:dark_oak_bark": return "minecraft:dark_oak_wood";
			case "minecraft:grass_path": return "minecraft:dirt_path";
			case "minecraft:grass": return "minecraft:short_grass";
			case "minecraft:sign": return "minecraft:oak_sign";
			case "minecraft:wall_sign": return "minecraft:oak_wall_sign";
			case "minecraft:cauldron":
				String level = state.properties().get("level");
				return level != null && !level.equals("0") ? "minecraft:water_cauldron" : name;
			default: return name;
		}
	}

	static Map<String, String> properties(BlockState state) {
		Map<String, String> properties = new TreeMap<>();
		state.properties().forEach((key, value) -> {
			String normalized = value(state.name(), key, value);
			if (normalized != null) properties.put(key, normalized);
		});
		return properties;
	}

	static Map<String, String> propertiesForTarget(BlockState state, int targetProtocol) {
		Map<String, String> properties = properties(state);
		if (targetProtocol >= 393) return properties;
		String name = name(state);
		if (name.endsWith("_leaves") && properties.containsKey("persistent")) {
			// LeavesFix maps decayable=false to persistent=true. Distance is computed
			// from neighbors; the legacy check_decay bit is a server maintenance flag.
			properties.put("decayable", Boolean.toString(!Boolean.parseBoolean(properties.remove("persistent"))));
		}
		if (name.equals("minecraft:attached_melon_stem") || name.equals("minecraft:attached_pumpkin_stem")) {
			// BlockStem stores age only and derives its attachment from the fruit.
			properties.remove("facing");
			properties.put("age", "7");
		}
		if (legacySkull(name, targetProtocol)) {
			// Rotation/type are block-entity data; legacy skull power/animation is
			// read from the world by the tile entity, never stored in metadata.
			if (properties.remove("rotation") != null) properties.put("facing", "up");
			properties.remove("powered");
			properties.put("nodrop", "false");
		}
		if (targetProtocol == 47 && name.equals("minecraft:command_block") && !state.legacy()) {
			// 1.8 has TRIGGERED, not directional/conditional command blocks. The
			// flattening table labels its untriggered metadata zero as facing=down.
			properties.put("facing", "down");
		}
		return properties;
	}

	static void removeDerivedLegacyProperties(String name, Map<String, String> properties) {
		// These classes compute connections in getActualState; their metadata
		// retains only the material/color, age, power or tripwire flags.
		if (name.endsWith("_fence") || name.endsWith("_pane") || name.endsWith("_wall")
			|| name.equals("minecraft:iron_bars") || name.equals("minecraft:redstone_wire")
			|| name.equals("minecraft:tripwire") || name.equals("minecraft:fire") || name.equals("minecraft:chorus_plant")) {
			properties.remove("north");
			properties.remove("east");
			properties.remove("south");
			properties.remove("west");
		}
		if (name.equals("minecraft:chorus_plant")) {
			// BlockChorusPlant.getMetaFromState returns zero; all six connections
			// are computed from neighbors by getActualState in 1.9 through 1.12.
			properties.remove("up");
			properties.remove("down");
		}
		if (name.endsWith("_wall") || name.equals("minecraft:fire") || name.equals("minecraft:vine")) properties.remove("up");
		if (name.endsWith("_stairs")) properties.remove("shape");
		if (name.endsWith("_fence_gate")) properties.remove("in_wall");
		if (name.endsWith("_trapdoor")) properties.remove("powered");
		if (name.equals("minecraft:chest") || name.equals("minecraft:trapped_chest")) properties.remove("type");
		if (name.equals("minecraft:grass_block") || name.equals("minecraft:podzol") || name.equals("minecraft:mycelium")) properties.remove("snowy");
		if (name.equals("minecraft:repeater")) properties.remove("locked");
		if (name.equals("minecraft:piston_head")) properties.remove("short");
		if (name.endsWith("_wood")) properties.remove("axis"); // legacy logs' all-bark variant has axis=NONE
		if (name.endsWith("_leaves")) {
			properties.remove("distance");
			properties.remove("check_decay");
		}
		// BlockDoor stores facing/open in the lower half and hinge/powered in the upper.
		if (name.endsWith("_door")) {
			if ("upper".equals(properties.get("half"))) {
				properties.remove("facing");
				properties.remove("open");
			} else if ("lower".equals(properties.get("half"))) {
				properties.remove("hinge");
				properties.remove("powered");
			}
		}
		if (name.endsWith("_bed") && "foot".equals(properties.get("part"))) properties.remove("occupied");
		// Vertical buttons store only UP/DOWN; vertical levers store an X/Z axis.
		if ("floor".equals(properties.get("face")) || "ceiling".equals(properties.get("face"))) {
			if (name.endsWith("_button")) properties.remove("facing");
			if (name.equals("minecraft:lever")) {
				properties.replace("facing", "south", "north");
				properties.replace("facing", "east", "west");
			}
		}
		if (name.equals("minecraft:note_block")) {
			// Pitch and previous power live in TileEntityNote; instrument comes from below.
			properties.remove("note");
			properties.remove("powered");
			properties.remove("instrument");
		}
	}

	private static boolean legacySkull(String name, int targetProtocol) {
		switch (name) {
			case "minecraft:skeleton_skull": case "minecraft:skeleton_wall_skull":
			case "minecraft:wither_skeleton_skull": case "minecraft:wither_skeleton_wall_skull":
			case "minecraft:zombie_head": case "minecraft:zombie_wall_head":
			case "minecraft:player_head": case "minecraft:player_wall_head":
			case "minecraft:creeper_head": case "minecraft:creeper_wall_head":
				return true;
			case "minecraft:dragon_head": case "minecraft:dragon_wall_head":
				return targetProtocol >= 107;
			default: return false;
		}
	}

	private static boolean legacyFlowerPot(String name) {
		// BlockFlowerPot.EnumFlowerType (1.8-1.12); newer plants must not alias cactus.
		switch (name) {
			case "minecraft:flower_pot":
			case "minecraft:potted_poppy": case "minecraft:potted_blue_orchid":
			case "minecraft:potted_allium": case "minecraft:potted_azure_bluet":
			case "minecraft:potted_red_tulip": case "minecraft:potted_orange_tulip":
			case "minecraft:potted_white_tulip": case "minecraft:potted_pink_tulip":
			case "minecraft:potted_oxeye_daisy": case "minecraft:potted_dandelion":
			case "minecraft:potted_oak_sapling": case "minecraft:potted_spruce_sapling":
			case "minecraft:potted_birch_sapling": case "minecraft:potted_jungle_sapling":
			case "minecraft:potted_acacia_sapling": case "minecraft:potted_dark_oak_sapling":
			case "minecraft:potted_red_mushroom": case "minecraft:potted_brown_mushroom":
			case "minecraft:potted_dead_bush": case "minecraft:potted_fern": case "minecraft:potted_cactus":
				return true;
			default: return false;
		}
	}

	static String value(String name, String property, String value) {
		if (name.equals("minecraft:cauldron") && property.equals("level") && value.equals("0")) return null;
		if (name.endsWith("_wall") && (property.equals("north") || property.equals("south") || property.equals("west") || property.equals("east"))) {
			if (value.equals("true")) return "low";
			if (value.equals("false")) return "none";
		}
		return value;
	}
}


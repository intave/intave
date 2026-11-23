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

package de.jpx3.intave.item.codec;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.item.*;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

/**
 * Minecraft's ItemStack.OPTIONAL_STREAM_CODEC and DataComponentPatch.STREAM_CODEC.
 * Nested components use the registry and wire layout of the enclosing stack.
 */
public final class ComponentItemStackCodec implements StreamCodec<ByteBuf, ByteBuf, ItemStack> {
	private static final MinecraftKey AIR = MinecraftKey.withDefaultNamespace("air");
	private static final ValueCodec INT = scalar(VARINT, v -> v.number().intValue());
	private static final ValueCodec FIXED_INT = scalar(INTEGER, v -> v.number().intValue());
	private static final ValueCodec FLOAT_VALUE = scalar(FLOAT, v -> v.number().floatValue());
	private static final ValueCodec DOUBLE_VALUE = scalar(DOUBLE, v -> v.number().doubleValue());
	private static final ValueCodec BOOL = scalar(BOOLEAN, ItemComponentValue::bool);
	private static final ValueCodec TEXT = text(32767);
	private static final ValueCodec UUID_VALUE = scalar(UUID, ItemComponentValue::uuid);
	private static final ValueCodec NBT = scalar(TAG, ItemComponentValue::nbt);
	private static final ValueCodec COMPOUND = scalar(COMPOUND_TAG, value -> (com.viaversion.nbt.tag.CompoundTag) value.nbt());
	private static final ValueCodec UNIT = new ValueCodec() {
		public ItemComponentValue read(ByteBuf input, int depth) {
			return ItemComponentValue.EMPTY;
		}

		public void write(ByteBuf output, ItemComponentValue value, int depth) {
		}
	};
	private final int protocol;
	private final ItemRegistry items;
	private final ItemComponentTypeRegistry types;
	private final Map<String, ValueCodec> values = new LinkedHashMap<>();

	public ComponentItemStackCodec(int protocol) {
		if (protocol < 766 || protocol > 777)
			throw new IllegalArgumentException("Unsupported component item protocol " + protocol);
		this.protocol = protocol;
		items = ItemRegistry.forProtocol(protocol);
		types = ItemComponentTypeRegistry.forProtocol(protocol);
		defineComponents();
		for (MinecraftKey key : types.componentTypeIds().keySet()) {
			if (!values.containsKey(key.path())) throw new IllegalStateException("Missing component codec: " + key);
		}
	}

	@Override
	public ItemStack decode(ByteBuf input) {
		int start = input.readerIndex();
		try {
			return readItem(input, 0);
		} catch (RuntimeException failure) {
			input.readerIndex(start);
			throw failure;
		}
	}

	@Override
	public void encode(ByteBuf output, ItemStack stack) {
		int start = output.writerIndex();
		try {
			writeItem(output, stack, 0);
		} catch (RuntimeException failure) {
			output.writerIndex(start);
			throw failure;
		}
	}

	private ItemStack readItem(ByteBuf input, int depth) {
		checkDepth(depth);
		int amount = VARINT.decode(input);
		if (amount <= 0) return null;
		MinecraftKey type = items.keyById(VARINT.decode(input));
		if (type == null) throw new IllegalArgumentException("Unknown component item ID");
		ItemComponentPatch patch = readPatch(input, depth);
		if (AIR.equals(type)) return null;
		ItemStack stack = new ItemStack(type, amount, protocol);
		stack.setComponents(patch);
		return stack;
	}

	private ItemComponentPatch readPatch(ByteBuf input, int depth) {
		int added = count(input, input.readableBytes());
		int removed = count(input, input.readableBytes());
		if (added == 0 && removed == 0) return ItemComponentPatch.EMPTY;
		Map<MinecraftKey, ItemComponentValue> additions = new LinkedHashMap<>();
		Set<MinecraftKey> removals = new LinkedHashSet<>();
		for (int i = 0; i < added; i++) {
			MinecraftKey key = readType(input);
			additions.put(key, values.get(key.path()).read(input, depth));
		}
		for (int i = 0; i < removed; i++) {
			MinecraftKey key = readType(input);
			additions.remove(key); // Vanilla's removal pass overrides an earlier addition.
			removals.add(key);
		}
		return new ItemComponentPatch(additions, removals);
	}

	private void writeItem(ByteBuf output, ItemStack stack, int depth) {
		checkDepth(depth);
		if (stack == null) {
			VARINT.encode(output, 0);
			return;
		}
		if (stack.protocol() != protocol) {
			throw new IllegalArgumentException("Component conversion between protocols is not supported");
		}
		if (stack.hasLegacyData()) {
			throw new IllegalArgumentException("Component stacks cannot carry legacy damage data");
		}
		int id = items.idOf(stack.type());
		if (id < 0) {
			throw new IllegalArgumentException("Unknown component item " + stack.type());
		}
		VARINT.encode(output, stack.amount());
		VARINT.encode(output, id);
		writePatch(output, stack.components(), depth);
	}

	private void writePatch(ByteBuf output, ItemComponentPatch patch, int depth) {
		VARINT.encode(output, patch.added().size());
		VARINT.encode(output, patch.removed().size());
		patch.added().forEach((key, value) -> {
			writeType(output, key);
			values.get(key.path()).write(output, value, depth);
		});
		patch.removed().forEach(key -> writeType(output, key));
	}

	private MinecraftKey readType(ByteBuf input) {
		MinecraftKey key = types.keyById(VARINT.decode(input));
		if (key == null) {
			throw new IllegalArgumentException("Unknown component type ID");
		}
		return key;
	}

	private void writeType(ByteBuf output, MinecraftKey key) {
		int id = types.idOf(key);
		if (id < 0) {
			throw new IllegalArgumentException("Unknown component type " + key);
		}
		VARINT.encode(output, id);
	}

	private void defineComponents() {
		// Components without networkSynchronized use DataComponentType.Builder's NBT codec.
		put(NBT, "custom_data", "intangible_projectile", "map_decorations", "debug_stick_state", "recipes", "lock", "container_loot");
		put(INT, "max_stack_size", "max_damage", "damage", "repair_cost", "ominous_bottle_amplifier", "map_id", "enchantable");
		put(BOOL, "unbreakable", "enchantment_glint_override");
		put(NBT, "custom_name", "item_name");
		put(TEXT, "item_model", "tooltip_style", "note_block_sound", "damage_resistant");
		put(UNIT, "hide_additional_tooltip", "hide_tooltip", "creative_slot_lock", "fire_resistant", "glider");
		put(COMPOUND, "entity_data", "bucket_entity_data", "block_entity_data");
		put(list(NBT, 256), "lore");
		put(mappedId(4), "rarity");
		put(mappedId(16), "base_color");
		put(mappedId(2), "map_post_processing");
		put(FIXED_INT, "map_color");
		put(enchantments(), "enchantments", "stored_enchantments");
		put(fields("rgb", FIXED_INT, "show_in_tooltip", protocol < 770 ? BOOL : null), "dyed_color");
		put(fields("properties", list(fields("name", TEXT, "value", TEXT))), "block_state");
		put(list(fields("entity_data", COMPOUND, "ticks_in_hive", INT, "min_ticks_in_hive", INT)), "bees");
		put(list(INT, 4), "pot_decorations");
		put(list(item(false), protocol >= 776 ? 1024 : Integer.MAX_VALUE), "charged_projectiles");
		put(list(item(false)), "bundle_contents");
		put(list(item(true), 256), "container");
		put(item(false), "use_remainder");

		ValueCodec holders = holderSet();
		ValueCodec properties = list(fields("name", TEXT, "matcher", either("exact", TEXT, "range", fields("min", optional(TEXT), "max", optional(TEXT)))));
		ValueCodec predicate = fields("blocks", optional(holders), "properties", optional(properties), "nbt", optional(COMPOUND),
			"components", protocol >= 770 ? componentMatchers() : null);
		put(fields("predicates", list(predicate), "show_in_tooltip", protocol < 770 ? BOOL : null), "can_place_on", "can_break");
		put(fields("rules", list(fields("blocks", holders, "speed", optional(FLOAT_VALUE), "correct_for_drops", optional(BOOL))),
			"default_mining_speed", FLOAT_VALUE, "damage_per_block", INT,
			"can_destroy_blocks_in_creative", protocol >= 770 ? BOOL : null), "tool");
		put(holders, "repairable");

		ValueCodec modifier = protocol == 766
			? fields("uuid", UUID_VALUE, "name", TEXT, "amount", DOUBLE_VALUE, "operation", mappedId(3))
			: fields("id", TEXT, "amount", DOUBLE_VALUE, "operation", mappedId(3));
		ValueCodec display = dispatch(mappedId(3), UNIT, UNIT, fields("component", NBT));
		put(fields("modifiers", list(fields("attribute", INT, "modifier", modifier, "slot", mappedId(protocol < 770 ? 10 : 11),
			"display", protocol >= 771 ? display : null)), "show_in_tooltip", protocol < 770 ? BOOL : null), "attribute_modifiers");
		ValueCodec effects = fields("id", INT, "details", effectDetails());
		ValueCodec possibleEffect = fields("effect", effects, "probability", FLOAT_VALUE);
		ValueCodec food = protocol == 766
			? fields("nutrition", INT, "saturation", FLOAT_VALUE, "can_always_eat", BOOL, "eat_seconds", FLOAT_VALUE, "effects", list(possibleEffect))
			: protocol == 767
			? fields("nutrition", INT, "saturation", FLOAT_VALUE, "can_always_eat", BOOL, "eat_seconds", FLOAT_VALUE,
			"using_converts_to", optional(item(false)), "effects", list(possibleEffect))
			: fields("nutrition", INT, "saturation", FLOAT_VALUE, "can_always_eat", BOOL);
		put(food, "food");
		put(protocol < 768
			? fields("potion", optional(INT), "custom_color", optional(FIXED_INT), "custom_effects", list(effects))
			: fields("potion", optional(INT), "custom_color", optional(FIXED_INT), "custom_effects", list(effects), "custom_name", optional(TEXT)), "potion_contents");
		put(list(fields("id", INT, "duration", INT)), "suspicious_stew_effects");

		put(list(filterable(text(1024)), 100), "writable_book_content");
		put(fields("title", filterable(text(32)), "author", TEXT, "generation", INT, "pages", list(filterable(NBT)), "resolved", BOOL), "written_book_content");
		put(fields("target", optional(fields("dimension", TEXT, "position", scalar(LONG, v -> v.number().longValue()))), "tracked", BOOL), "lodestone_tracker");
		ValueCodec explosion = fields("shape", mappedId(5), "colors", list(FIXED_INT), "fade_colors", list(FIXED_INT), "trail", BOOL, "twinkle", BOOL);
		put(explosion, "firework_explosion");
		put(fields("flight_duration", INT, "explosions", list(explosion, 256)), "fireworks");
		put(fields("name", optional(text(16)), "id", optional(UUID_VALUE), "properties",
			list(fields("name", text(64), "value", TEXT, "signature", optional(text(1024))), 16)), "profile");

		ValueCodec sound = holder(fields("id", TEXT, "range", optional(FLOAT_VALUE)));
		ValueCodec instrument = protocol < 768
			? fields("sound", sound, "use_duration", INT, "range", FLOAT_VALUE)
			: fields("sound", sound, "use_duration", FLOAT_VALUE, "range", FLOAT_VALUE,
				"durability_damage", protocol >= 777 ? INT : null, "description", NBT);
		put(protocol >= 770 ? holderOrKey(holder(instrument)) : holder(instrument), "instrument");
		ValueCodec trimMaterial = protocol < 769
			? fields("asset_name", TEXT, "ingredient", INT, "item_model_index", FLOAT_VALUE,
			"overrides", list(fields("material", protocol < 768 ? INT : TEXT, "asset", TEXT)), "description", NBT)
			: fields("asset_name", TEXT, "ingredient", INT, "overrides", list(fields("material", TEXT, "asset", TEXT)), "description", NBT);
		if (protocol >= 770) {
			trimMaterial = protocol >= 777 ? fields("palette_id", TEXT, "description", NBT)
				: fields("base", TEXT, "overrides", list(fields("material", TEXT, "asset", TEXT)), "description", NBT);
			put(holderOrKey(holder(trimMaterial)), "provides_trim_material");
		}
		ValueCodec trimPattern = fields("asset_id", TEXT, "template_item", protocol < 770 ? INT : null, "description", NBT, "decal", BOOL);
		put(fields("material", holder(trimMaterial), "pattern", holder(trimPattern), "show_in_tooltip", protocol < 770 ? BOOL : null), "trim");
		put(list(fields("pattern", holder(fields("asset_id", TEXT, "translation_key", TEXT)), "color", mappedId(16))), "banner_patterns");
		ValueCodec song = holder(fields("sound", sound, "description", NBT, "length_in_seconds", FLOAT_VALUE, "comparator_output", INT));
		put(fields("song", holderOrKey(song), "show_in_tooltip", protocol < 770 ? BOOL : null), "jukebox_playable");

		ValueCodec consumeEffect = dispatch(INT,
			fields("effects", list(effects), "probability", FLOAT_VALUE), fields("effects", holders), UNIT,
			fields("diameter", FLOAT_VALUE), fields("sound", sound)
		);
		put(fields("consume_seconds", FLOAT_VALUE, "animation", mappedId(protocol < 769 ? 10 : protocol < 774 ? 11 : 12), "sound", sound, "has_consume_particles", BOOL,
			"on_consume_effects", list(consumeEffect)), "consumable");
		put(list(consumeEffect), "death_protection");
		put(fields("seconds", FLOAT_VALUE, "cooldown_group", optional(TEXT)), "use_cooldown");
		put(fields("slot", mappedId(protocol < 770 ? 7 : 8), "equip_sound", sound, "asset_id", optional(TEXT), "camera_overlay", optional(TEXT),
			"allowed_entities", optional(holders), "dispensable", BOOL, "swappable", BOOL, "damage_on_hurt", BOOL,
			"equip_on_interact", protocol >= 770 ? BOOL : null, "can_be_sheared", protocol >= 771 ? BOOL : null,
			"shearing_sound", protocol >= 771 ? sound : null), "equippable");
		put(protocol < 769 ? INT : fields("floats", list(FLOAT_VALUE), "flags", list(BOOL), "strings", list(TEXT), "colors", list(FIXED_INT)), "custom_model_data");
		if (protocol >= 770) {
			defineModernComponents(sound, holders);
		}
	}

	private void put(ValueCodec codec, String... keys) {
		for (String key : keys) values.put(key, codec);
	}

	private ValueCodec holderOrKey(ValueCodec holder) {
		return protocol >= 775 ? holder : either("holder", holder, "key", TEXT);
	}

	private void defineModernComponents(ValueCodec sound, ValueCodec holders) {
		put(UNIT, "unbreakable", "waxed");
		put(fields("hide_tooltip", BOOL, "hidden_components", list(INT)), "tooltip_display");
		put(fields("item_damage_per_attack", INT, "disable_blocking_for_seconds", FLOAT_VALUE), "weapon");
		put(FLOAT_VALUE, "potion_duration_scale", "minimum_attack_charge");
		put(sound, "break_sound");
		put(protocol >= 775 ? holders : TEXT, "provides_banner_patterns", "damage_resistant");
		ValueCodec reduction = fields("horizontal_blocking_angle", FLOAT_VALUE, "type", optional(holders),
			"base", FLOAT_VALUE, "factor", FLOAT_VALUE);
		put(fields("block_delay_seconds", FLOAT_VALUE, "disable_cooldown_scale", FLOAT_VALUE,
			"damage_reductions", list(reduction), "item_damage", fields("threshold", FLOAT_VALUE, "base", FLOAT_VALUE, "factor", FLOAT_VALUE),
			"bypassed_by", optional(protocol >= 775 ? holders : TEXT), "block_sound", optional(sound), "disable_sound", optional(sound)), "blocks_attacks");

		put(INT, "villager/variant", "wolf/variant", "wolf/sound_variant", "pig/variant", "cow/variant", "frog/variant",
			"cat/variant", "pig/sound_variant", "cow/sound_variant", "chicken/sound_variant", "cat/sound_variant");
		put(holderOrKey(INT), "chicken/variant", "zombie_nautilus/variant", "damage_type");
		put(mappedId(16), "wolf/collar", "cat/collar", "tropical_fish/base_color", "tropical_fish/pattern_color",
			"sheep/color", "shulker/color", "cushion/color", "dye");
		put(mappedId(2), "fox/variant");
		put(mappedId(5), "axolotl/variant");
		put(clampedId(2), "mooshroom/variant");
		put(clampedId(5), "parrot/variant");
		put(clampedId(4), "llama/variant");
		put(clampedId(3), "salmon/size");
		put(mappedId(id -> Math.floorMod(id, 7)), "horse/variant");
		put(mappedId(id -> id >= 0 && id <= 5 || id == 99 ? id : 0), "rabbit/variant");
		// The high byte selects one of six patterns and the low byte selects small or large.
		put(mappedId(id -> (id >>> 8) < 6 && (id & 255) < 2 ? id : 0), "tropical_fish/pattern");
		put(holder(fields("width", INT, "height", INT, "asset_id", TEXT,
			"title", optional(NBT), "author", optional(NBT))), "painting/variant");

		if (protocol >= 773) {
			ValueCodec typedEntity = fields("type", INT, "tag", COMPOUND);
			put(typedEntity, "entity_data", "block_entity_data");
			put(list(fields("entity_data", typedEntity, "ticks_in_hive", INT, "min_ticks_in_hive", INT)), "bees");
			put(profileWithSkin(), "profile");
		}
		if (protocol >= 774) {
			put(fields("can_sprint", BOOL, "interact_vibrations", BOOL, "speed_multiplier", FLOAT_VALUE), "use_effects");
			put(fields("min_reach", FLOAT_VALUE, "max_reach", FLOAT_VALUE, "min_creative_reach", FLOAT_VALUE,
				"max_creative_reach", FLOAT_VALUE, "hitbox_margin", FLOAT_VALUE, "mob_factor", FLOAT_VALUE), "attack_range");
			put(fields("deals_knockback", BOOL, "dismounts", BOOL, "sound", optional(sound), "hit_sound", optional(sound)), "piercing_weapon");
			ValueCodec condition = fields("max_duration_ticks", INT, "min_speed", FLOAT_VALUE, "min_relative_speed", FLOAT_VALUE);
			put(fields("contact_cooldown_ticks", INT, "delay_ticks", INT, "dismount_conditions", optional(condition),
				"knockback_conditions", optional(condition), "damage_conditions", optional(condition), "forward_movement", FLOAT_VALUE,
				"damage_multiplier", FLOAT_VALUE, "sound", optional(sound), "hit_sound", optional(sound)), "kinetic_weapon");
			put(fields("type", mappedId(3), "duration", INT), "swing_animation", "attack_animation", "interact_animation");
			put(INT, "additional_trade_cost");
		}
		if (protocol >= 776) {
			put(itemTemplate(), "sulfur_cube_content");
		}
		if (protocol >= 777) {
			put(INT, "block_transformer", "provides_pottery_pattern", "villager_food");
			put(fields("targeting_entity_types", holders, "visibility", FLOAT_VALUE), "mob_visibility");
			ValueCodec integer = either("constant", FIXED_INT, "reference", TEXT);
			ValueCodec speed = either("constant", FLOAT_VALUE, "reference", TEXT);
			put(fields("layers", integer), "compostable");
			put(fields("burn_time", integer, "speed_multiplier", speed), "cooking_fuel");
			put(fields("uses", integer, "speed_multiplier", speed), "brewing_fuel");
			ValueCodec side = optional(itemTemplate());
			put(fields("back", side, "left", side, "right", side, "front", side), "pot_decorations");
			ValueCodec lines = fourSignLines();
			put(fields("messages", lines, "filtered_messages", optional(lines), "color", mappedId(16),
				"has_glowing_text", BOOL), "sign_text_front", "sign_text_back");
		}
	}

	private ValueCodec componentMatchers() {
		ValueCodec exact = new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				checkDepth(depth);
				MinecraftKey key = readType(input);
				Map<String, ItemComponentValue> entry = new LinkedHashMap<>();
				entry.put("type", ItemComponentValue.of(key.fullKey()));
				entry.put("value", values.get(key.path()).read(input, depth + 1));
				return ItemComponentValue.fields(entry);
			}

			public void write(ByteBuf output, ItemComponentValue entry, int depth) {
				checkDepth(depth);
				MinecraftKey key = MinecraftKey.parse(entry.field("type").string());
				writeType(output, key);
				values.get(key.path()).write(output, entry.field("value"), depth + 1);
			}
		};
		ValueCodec type = protocol >= 774 ? either("predicate", INT, "component", INT) : INT;
		return fields("exact", list(exact), "partial", list(fields("type", type, "predicate", NBT), 64));
	}

	private ValueCodec profileWithSkin() {
		ValueCodec properties = list(fields("name", text(64), "value", TEXT, "signature", optional(text(1024))), 16);
		ValueCodec full = fields("id", UUID_VALUE, "name", text(16), "properties", properties);
		ValueCodec partial = fields("name", optional(text(16)), "id", optional(UUID_VALUE), "properties", properties);
		ValueCodec skin = fields("body", optional(TEXT), "cape", optional(TEXT), "elytra", optional(TEXT), "slim", optional(BOOL));
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				boolean complete = input.readBoolean();
				Map<String, ItemComponentValue> fields = new LinkedHashMap<>((complete ? full : partial).read(input, depth).fields());
				fields.put("complete", ItemComponentValue.of(complete));
				fields.put("skin", skin.read(input, depth));
				return ItemComponentValue.fields(fields);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				boolean complete = value.field("complete") != null && value.field("complete").bool();
				output.writeBoolean(complete);
				(complete ? full : partial).write(output, value, depth);
				skin.write(output, value.field("skin") == null ? ItemComponentValue.EMPTY : value.field("skin"), depth);
			}
		};
	}

	private static ValueCodec fourSignLines() {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				List<ItemComponentValue> lines = new ArrayList<>(4);
				for (int i = 0; i < 4; i++) lines.add(NBT.read(input, depth));
				return ItemComponentValue.list(lines);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				if (value.elements().size() != 4) throw new IllegalArgumentException("Expected four sign lines");
				for (ItemComponentValue line : value.elements()) NBT.write(output, line, depth);
			}
		};
	}

	/** ItemStackTemplate puts the item ID first and keeps its own count and patch. */
	private ValueCodec itemTemplate() {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				checkDepth(depth);
				MinecraftKey type = items.keyById(VARINT.decode(input));
				int amount = VARINT.decode(input);
				if (type == null || AIR.equals(type) || amount == 0) throw new IllegalArgumentException("Invalid item template");
				Map<String, ItemComponentValue> fields = new LinkedHashMap<>();
				fields.put("item", ItemComponentValue.of(type.fullKey()));
				fields.put("count", ItemComponentValue.of(amount));
				fields.put("components", ItemComponentValue.of(readPatch(input, depth + 1)));
				return ItemComponentValue.fields(fields);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				checkDepth(depth);
				MinecraftKey type = MinecraftKey.parse(value.field("item").string());
				int id = items.idOf(type);
				int amount = value.field("count").number().intValue();
				if (id < 0 || AIR.equals(type) || amount == 0) throw new IllegalArgumentException("Invalid item template");
				VARINT.encode(output, id);
				VARINT.encode(output, amount);
				writePatch(output, value.field("components").patch(), depth + 1);
			}
		};
	}

	private ValueCodec item(boolean allowEmpty) {
		if (protocol >= 775) {
			ValueCodec template = itemTemplate();
			if (!allowEmpty) return template;
			return new ValueCodec() {
				public ItemComponentValue read(ByteBuf input, int depth) {
					return input.readBoolean() ? template.read(input, depth) : ItemComponentValue.EMPTY;
				}

				public void write(ByteBuf output, ItemComponentValue value, int depth) {
					output.writeBoolean(!value.isEmpty());
					if (!value.isEmpty()) template.write(output, value, depth);
				}
			};
		}
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				ItemStack stack = readItem(input, depth + 1);
				if (stack == null && !allowEmpty) throw new IllegalArgumentException("Empty nested item is not allowed");
				return stack == null ? ItemComponentValue.EMPTY : ItemComponentValue.of(stack);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				if (value.isEmpty() && !allowEmpty) throw new IllegalArgumentException("Empty nested item is not allowed");
				writeItem(output, value.isEmpty() ? null : value.item(), depth + 1);
			}
		};
	}

	private static ValueCodec effectDetails() {
		return new ValueCodec() {
			private ValueCodec body() {
				ValueCodec amplifier = scalar(VARINT.beforeAndAfter(value -> Math.max(0, Math.min(255, value)), value -> value), v -> v.number().intValue());
				return fields("amplifier", amplifier, "duration", INT, "ambient", BOOL, "show_particles", BOOL, "show_icon", BOOL, "hidden_effect", optional(this));
			}

			public ItemComponentValue read(ByteBuf input, int depth) {
				checkDepth(depth);
				return body().read(input, depth + 1);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				checkDepth(depth);
				body().write(output, value, depth + 1);
			}
		};
	}

	private ValueCodec enchantments() {
		ValueCodec body = fields("enchantments", list(fields("id", INT, "level", INT)), "show_in_tooltip", protocol < 770 ? BOOL : null);
		return new ValueCodec() {
			private ItemComponentValue normalize(ItemComponentValue value) {
				Map<Integer, ItemComponentValue> entries = new LinkedHashMap<>();
				for (ItemComponentValue entry : value.field("enchantments").elements())
					entries.put(entry.field("id").number().intValue(), entry);
				for (ItemComponentValue entry : entries.values()) {
					int level = entry.field("level").number().intValue();
					if (level < 0 || level > 255) throw new IllegalArgumentException("Invalid enchantment level " + level);
				}
				Map<String, ItemComponentValue> fields = new LinkedHashMap<>(value.fields());
				fields.put("enchantments", ItemComponentValue.list(new ArrayList<>(entries.values())));
				return ItemComponentValue.fields(fields);
			}

			public ItemComponentValue read(ByteBuf input, int depth) {
				return normalize(body.read(input, depth));
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				body.write(output, normalize(value), depth);
			}
		};
	}

	/**
	 * ByIdMap.OutOfBoundsStrategy.ZERO, used by vanilla's component enums.
	 */
	private static ValueCodec mappedId(int size) {
		return mappedId(id -> id >= 0 && id < size ? id : 0);
	}

	private static ValueCodec clampedId(int size) {
		return mappedId(id -> Math.max(0, Math.min(size - 1, id)));
	}

	private static ValueCodec mappedId(IntUnaryOperator normalize) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				return ItemComponentValue.of(normalize.applyAsInt(VARINT.decode(input)));
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				int id = value.number().intValue();
				if (id != normalize.applyAsInt(id)) throw new IllegalArgumentException("Invalid component enum ID " + id);
				VARINT.encode(output, id);
			}
		};
	}

	private static void checkDepth(int depth) {
		if (depth > 512) throw new IllegalArgumentException("Component nesting exceeds 512 levels");
	}

	private interface ValueCodec {
		ItemComponentValue read(ByteBuf input, int depth);

		void write(ByteBuf output, ItemComponentValue value, int depth);
	}

	private static <T> ValueCodec scalar(StreamCodec<ByteBuf, ByteBuf, T> codec, Function<ItemComponentValue, T> unwrap) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				T value = codec.decode(input);
				if (value == null) throw new IllegalArgumentException("Null component value");
				return ItemComponentValue.of(value);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				codec.encode(output, unwrap.apply(value));
			}
		};
	}

	private static ValueCodec text(int limit) {
		return scalar(stringCodec(limit), ItemComponentValue::string);
	}

	/**
	 * Optional fields are omitted from the decoded field map.
	 */
	private static ValueCodec optional(ValueCodec codec) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				return input.readBoolean() ? codec.read(input, depth) : null;
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				output.writeBoolean(value != null);
				if (value != null) codec.write(output, value, depth);
			}
		};
	}

	private static ValueCodec fields(Object... fields) {
		// A null codec omits a field in this protocol. Select the layout once when the codec is built.
		List<Object> layout = new ArrayList<>(fields.length);
		for (int i = 0; i < fields.length; i += 2) {
			if (fields[i + 1] != null) {
				layout.add(fields[i]);
				layout.add(fields[i + 1]);
			}
		}
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				Map<String, ItemComponentValue> values = new LinkedHashMap<>();
				for (int i = 0; i < layout.size(); i += 2) {
					ItemComponentValue value = ((ValueCodec) layout.get(i + 1)).read(input, depth);
					if (value != null) values.put((String) layout.get(i), value);
				}
				return ItemComponentValue.fields(values);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				for (int i = 0; i < layout.size(); i += 2)
					((ValueCodec) layout.get(i + 1)).write(output, value.field((String) layout.get(i)), depth);
			}
		};
	}

	private static ValueCodec list(ValueCodec codec) {
		return list(codec, Integer.MAX_VALUE);
	}

	private static ValueCodec list(ValueCodec codec, int maximum) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				// Every element in these component lists consumes at least one byte.
				int size = count(input, Math.min(maximum, input.readableBytes()));
				List<ItemComponentValue> values = new ArrayList<>(Math.min(size, 65536));
				for (int i = 0; i < size; i++) values.add(codec.read(input, depth));
				return ItemComponentValue.list(values);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				List<ItemComponentValue> elements = value.elements();
				if (elements.size() > maximum) throw new IllegalArgumentException("Too many component elements");
				VARINT.encode(output, elements.size());
				for (ItemComponentValue element : elements) codec.write(output, element, depth);
			}
		};
	}

	private static ValueCodec filterable(ValueCodec codec) {
		return fields("raw", codec, "filtered", optional(codec));
	}

	private static ValueCodec either(String leftName, ValueCodec left, String rightName, ValueCodec right) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				boolean isLeft = input.readBoolean();
				return ItemComponentValue.fields(Collections.singletonMap(isLeft ? leftName : rightName, (isLeft ? left : right).read(input, depth)));
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				boolean isLeft = value.field(leftName) != null;
				output.writeBoolean(isLeft);
				(isLeft ? left : right).write(output, value.field(isLeft ? leftName : rightName), depth);
			}
		};
	}

	/**
	 * A referenced holder stores its unshifted registry ID; an inline holder stores its value.
	 */
	private static ValueCodec holder(ValueCodec direct) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				int id = VARINT.decode(input);
				if (id < 0) throw new IllegalArgumentException("Negative holder ID");
				return ItemComponentValue.fields(Collections.singletonMap(id == 0 ? "value" : "id",
					id == 0 ? direct.read(input, depth) : ItemComponentValue.of(id - 1)));
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				ItemComponentValue id = value.field("id");
				VARINT.encode(output, id == null ? 0 : Math.addExact(id.number().intValue(), 1));
				if (id == null) direct.write(output, value.field("value"), depth);
			}
		};
	}

	private static ValueCodec holderSet() {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				int size = VARINT.decode(input);
				if (size == 0) return ItemComponentValue.fields(Collections.singletonMap("tag", TEXT.read(input, depth)));
				if (size < 0 || size - 1 > input.readableBytes()) throw new IllegalArgumentException("Invalid holder set size");
				List<ItemComponentValue> ids = new ArrayList<>(Math.min(size - 1, 65536));
				for (int i = 1; i < size; i++) ids.add(INT.read(input, depth));
				return ItemComponentValue.fields(Collections.singletonMap("ids", ItemComponentValue.list(ids)));
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				if (value.field("tag") != null) {
					VARINT.encode(output, 0);
					TEXT.write(output, value.field("tag"), depth);
				} else {
					List<ItemComponentValue> ids = value.field("ids").elements();
					VARINT.encode(output, Math.addExact(ids.size(), 1));
					for (ItemComponentValue id : ids) INT.write(output, id, depth);
				}
			}
		};
	}

	private static ValueCodec dispatch(ValueCodec type, ValueCodec... variants) {
		return new ValueCodec() {
			public ItemComponentValue read(ByteBuf input, int depth) {
				int id = type.read(input, depth).number().intValue();
				if (id < 0 || id >= variants.length) throw new IllegalArgumentException("Unknown component variant " + id);
				Map<String, ItemComponentValue> fields = new LinkedHashMap<>();
				fields.put("type", ItemComponentValue.of(id));
				fields.put("value", variants[id].read(input, depth));
				return ItemComponentValue.fields(fields);
			}

			public void write(ByteBuf output, ItemComponentValue value, int depth) {
				int id = value.field("type").number().intValue();
				if (id < 0 || id >= variants.length) throw new IllegalArgumentException("Unknown component variant " + id);
				type.write(output, value.field("type"), depth);
				variants[id].write(output, value.field("value"), depth);
			}
		};
	}
}

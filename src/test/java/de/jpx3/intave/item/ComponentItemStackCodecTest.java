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

import com.viaversion.nbt.tag.StringTag;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;
import static org.junit.jupiter.api.Assertions.*;

final class ComponentItemStackCodecTest {
	private static final MinecraftKey STONE = key("stone");

	@Test
	void decodesAndReencodesFixturesProducedByVanillaComponentCodecs() throws IOException {
		for (int protocol = 767; protocol <= 777; protocol++) {
			StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
			InputStream resource = getClass().getResourceAsStream("/item-components/" + protocol + ".tsv");
			assertNotNull(resource);
			int count = 0;
			Set<MinecraftKey> coveredTypes = new HashSet<>();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					String[] parts = line.split("\t");
					coveredTypes.add(MinecraftKey.from(parts[0]));
					byte[] bytes = Base64.getDecoder().decode(parts[1]);
					ByteBuf input = Unpooled.wrappedBuffer(bytes), output = Unpooled.buffer();
					try {
						ItemStack item;
						try {
							item = codec.decode(input);
						} catch (RuntimeException failure) {
							throw new AssertionError("Decoding " + parts[0] + " on protocol " + protocol, failure);
						}
						assertNotNull(item.components().get(MinecraftKey.from(parts[0])), line);
						assertFalse(input.isReadable(), protocol + " " + parts[0]);
						codec.encode(output, item);
						byte[] encoded = new byte[output.readableBytes()];
						output.readBytes(encoded);
						assertArrayEquals(bytes, encoded, protocol + " " + parts[0]);
					} finally { input.release(); output.release(); }
					count++;
				}
			}
			assertTrue(count >= 50, "Vanilla fixtures were not exported");
			assertEquals(ItemComponentTypeRegistry.forProtocol(protocol).componentTypeIds().keySet(), coveredTypes);
		}
	}

	@Test
	void enchantmentIdsCanBeResolvedAgainstTheConnectionRegistry() {
		EnchantmentRegistry connection = EnchantmentRegistry.fromNetworkOrder(Arrays.asList(key("fortune"), key("sharpness")));
		ItemStack stack = new ItemStack(STONE, 1, 767);
		stack.setEnchantments(ItemEnchantments.of(Collections.singletonMap(key("sharpness"), 4)), connection);
		ByteBuf buffer = Unpooled.buffer();
		try {
			ItemStack.codecFor(767).encode(buffer, stack);
			ItemStack decoded = ItemStack.codecFor(767).decode(buffer);
			assertEquals(1, decoded.components().get(key("enchantments")).field("enchantments").elements().get(0).field("id").number().intValue());
			assertEquals(4, decoded.enchantments(connection).enchantmentLevel(key("sharpness")));
		} finally { buffer.release(); }
	}

	@Test
	void handlesProfilesEnchantmentsNestedStacksAndComponentRemoval() {
		for (int protocol : new int[]{766, 767, 768, 769}) {
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				start(input, protocol, 4, 1);
				type(input, protocol, "profile");
				input.writeBoolean(true);
				VARINT_STRING.encode(input, "Alex");
				input.writeBoolean(true).writeLong(12).writeLong(34).writeByte(1);
				VARINT_STRING.encode(input, "textures");
				VARINT_STRING.encode(input, "texture-data");
				input.writeBoolean(true);
				VARINT_STRING.encode(input, "signature");
				type(input, protocol, "enchantments");
				input.writeByte(1);
				VARINT.encode(input, EnchantmentRegistry.forProtocol(protocol).idOf(key("sharpness")));
				input.writeByte(5).writeBoolean(false);
				type(input, protocol, "container");
				input.writeByte(2).writeByte(0); // First slot is empty.
				start(input, protocol, 1, 0);
				type(input, protocol, "custom_name");
				TAG.encode(input, new StringTag("Nested item"));
				type(input, protocol, "damage");
				input.writeByte(7);
				type(input, protocol, "damage"); // Removal overrides the added damage value.
				input.writeInt(0x12345678);
				ItemStack item = ItemStack.codecFor(protocol).decode(input);
				assertEquals(0x12345678, input.readInt());
				assertEquals("Alex", item.skullOwnerName().get());
				assertEquals(5, item.enchantments().enchantmentLevel(key("sharpness")));
				assertNull(item.components().get(key("damage")));
				assertTrue(item.components().removed().contains(key("damage")));
				List<ItemComponentValue> nested = item.components().get(key("container")).elements();
				assertTrue(nested.get(0).isEmpty());
				assertEquals("Nested item", ((StringTag) nested.get(1).item().components().get(key("custom_name")).nbt()).getValue());
				item.setSkullOwnerName("Steve");
				item.setEnchantments(ItemEnchantments.of(Collections.singletonMap(key("sharpness"), 2)));
				ItemStack.codecFor(protocol).encode(output, item);
				ItemStack edited = ItemStack.codecFor(protocol).decode(output);
				assertEquals("Steve", edited.skullOwnerName().get());
				assertEquals(new java.util.UUID(12, 34), edited.components().get(key("profile")).field("id").uuid());
				assertEquals("signature", edited.components().get(key("profile")).field("properties").elements().get(0).field("signature").string());
				assertFalse(edited.components().get(key("enchantments")).field("show_in_tooltip").bool());
				assertEquals(2, edited.enchantments().enchantmentLevel(key("sharpness")));
			} finally { input.release(); output.release(); }
		}
	}

	@Test
	void readsTheChangedFoodAttributeAndCustomModelLayoutsAtTheirVersionBoundaries() {
		for (int protocol : new int[]{766, 767, 768, 769}) {
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				start(input, protocol, 3, 0);
				type(input, protocol, "food");
				input.writeByte(4).writeFloat(0.6f).writeBoolean(true);
				if (protocol < 768) {
					input.writeFloat(1.6f);
					if (protocol == 767) input.writeBoolean(false);
					input.writeByte(0);
				}
				type(input, protocol, "attribute_modifiers");
				input.writeByte(1).writeByte(0);
				if (protocol == 766) { input.writeLong(1).writeLong(2); VARINT_STRING.encode(input, "modifier"); }
				else VARINT_STRING.encode(input, "minecraft:modifier");
				input.writeDouble(3.5).writeByte(0).writeByte(0).writeBoolean(true);
				type(input, protocol, "custom_model_data");
				if (protocol < 769) input.writeByte(42);
				else {
					input.writeByte(1).writeFloat(3.25f).writeByte(1).writeBoolean(true).writeByte(1);
					VARINT_STRING.encode(input, "model");
					input.writeByte(1).writeInt(0x123456);
				}
				int size = input.readableBytes();
				ItemStack stack = ItemStack.codecFor(protocol).decode(input);
				assertFalse(input.isReadable());
				assertEquals(4, stack.components().get(key("food")).field("nutrition").number().intValue());
				ItemComponentValue modifier = stack.components().get(key("attribute_modifiers")).field("modifiers").elements().get(0).field("modifier");
				assertEquals(3.5, modifier.field("amount").number().doubleValue());
				if (protocol == 769) assertEquals(3.25f, stack.components().get(key("custom_model_data")).field("floats").elements().get(0).number().floatValue());
				ItemStack.codecFor(protocol).encode(output, stack);
				assertEquals(input.readerIndex(0), output);
				assertEquals(size, output.readableBytes());
			} finally { input.release(); output.release(); }
		}
	}

	@Test
	void malformedComponentsRollBackAndNeverConsumeFollowingFields() {
		for (int protocol : new int[]{766, 769}) {
			for (int malformed : new int[]{0, 1, 2, 3}) {
				ByteBuf input = Unpooled.buffer();
				try {
					start(input, protocol, 1, 0);
					if (malformed == 0) VARINT.encode(input, 9999);
					else if (malformed == 1) { type(input, protocol, "profile"); input.writeBoolean(true).writeByte(16).writeByte('x'); }
					else if (malformed == 2) { type(input, protocol, "lore"); VARINT.encode(input, 257); }
					else { type(input, protocol, "charged_projectiles"); input.writeByte(1).writeByte(0); }
					assertThrows(RuntimeException.class, () -> ItemStack.codecFor(protocol).decode(input));
					assertEquals(0, input.readerIndex());
				} finally { input.release(); }
			}
		}
	}

	@Test
	void appliesVanillaEnumFallbackAndValidatesTheFinalEnchantmentMap() {
		ByteBuf input = Unpooled.buffer();
		try {
			start(input, 769, 2, 0);
			type(input, 769, "rarity");
			VARINT.encode(input, 99);
			type(input, 769, "enchantments");
			input.writeByte(2).writeByte(0);
			VARINT.encode(input, -1);
			input.writeByte(0).writeByte(3).writeBoolean(true);
			ItemStack item = ItemStack.codecFor(769).decode(input);
			assertEquals(0, item.components().get(key("rarity")).number().intValue());
			assertEquals(1, item.components().get(key("enchantments")).field("enchantments").elements().size());
			input.clear();
			start(input, 769, 1, 0);
			type(input, 769, "enchantments");
			input.writeByte(1).writeByte(0);
			VARINT.encode(input, 256);
			input.writeBoolean(true);
			assertThrows(IllegalArgumentException.class, () -> ItemStack.codecFor(769).decode(input));
			assertEquals(0, input.readerIndex());
		} finally { input.release(); }
	}

	@Test
	void bundleUseAnimationIsIntroducedIn1214() {
		for (int protocol : new int[]{768, 769}) {
			ByteBuf input = Unpooled.buffer();
			try {
				start(input, protocol, 1, 0);
				type(input, protocol, "consumable");
				input.writeFloat(1.6f).writeByte(10).writeByte(1).writeBoolean(true).writeByte(0);
				ItemStack item = ItemStack.codecFor(protocol).decode(input);
				assertEquals(protocol == 768 ? 0 : 10, item.components().get(key("consumable")).field("animation").number().intValue());
				assertFalse(input.isReadable());
			} finally { input.release(); }
		}
	}

	private static void start(ByteBuf input, int protocol, int added, int removed) {
		input.writeByte(1);
		VARINT.encode(input, ItemRegistry.forProtocol(protocol).idOf(STONE));
		VARINT.encode(input, added);
		VARINT.encode(input, removed);
	}

	private static void type(ByteBuf input, int protocol, String name) {
		VARINT.encode(input, ItemComponentTypeRegistry.forProtocol(protocol).idOf(key(name)));
	}

	private static MinecraftKey key(String path) { return MinecraftKey.withDefaultNamespace(path); }
}

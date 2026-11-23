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

import com.viaversion.nbt.tag.CompoundTag;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.MinecraftKey;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.VARINT;
import static org.junit.jupiter.api.Assertions.*;

final class NbtItemStackCodecTest {
	private static final MinecraftKey SHARPNESS = MinecraftKey.withDefaultNamespace("sharpness");

	@Test
	void decodesAndPreservesCompleteTagsAcrossAllLegacyClickProtocols() throws IOException {
		for (int protocol : ItemRegistry.supportedProtocolVersions()) {
			if (protocol >= 755) continue;
			StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
			ByteBuf input = fixture(protocol, false), output = Unpooled.buffer();
			ByteBuf expected = fixture(protocol, false, "");
			try {
				ItemStack stack = codec.decode(input);
				assertFalse(input.isReadable());
				assertEquals(MinecraftKey.withDefaultNamespace("diamond_sword"), stack.type());
				assertEquals(3, stack.amount());
				assertEquals(protocol < 393 ? Short.valueOf((short) 123) : null, stack.legacyData());
				assertEquals(5, stack.enchantments().enchantmentLevel(SHARPNESS));
				assertEquals(Optional.of("Alex"), stack.skullOwnerName());
				assertEquals("custom-data", stack.nbt().getString("Uninterpreted"));
				codec.encode(output, stack);
				assertEquals(expected, output, "protocol " + protocol);
			} finally {
				input.release();
				output.release();
				expected.release();
			}
		}
	}

	@Test
	void storedEnchantmentsAreReadForBooks() throws IOException {
		for (int protocol : new int[]{47, 340, 393, 404, 754}) {
			ByteBuf input = fixture(protocol, true);
			try {
				ItemStack book = ItemStack.codecFor(protocol).decode(input);
				assertEquals(5, book.enchantments().enchantmentLevel(SHARPNESS));
				assertNotNull(book.nbt().getListTag("StoredEnchantments"));
			} finally {
				input.release();
			}
		}
	}

	@Test
	void amountEditsPreserveNbtValuesWithANormalizedRootName() throws IOException {
		for (int protocol : new int[]{47, 393, 401, 404, 754}) {
			ByteBuf input = fixture(protocol, false), output = Unpooled.buffer();
			ByteBuf expected = fixture(protocol, false, "");
			try {
				StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
				ItemStack stack = codec.decode(input);
				stack.setAmount(7);
				codec.encode(output, stack.copy());
				if (protocol < 404) expected.readShort();
				else { expected.readBoolean(); VARINT.decode(expected); }
				expected.setByte(expected.readerIndex(), 7);
				assertEquals(expected.readerIndex(0), output);
			} finally {
				input.release();
				output.release();
				expected.release();
			}
		}
	}

	@Test
	void propertyEditsAndCopiesPreserveOtherTagsAndProfileProperties() throws IOException {
		for (int protocol : new int[]{47, 393, 404, 754}) {
			ByteBuf input = fixture(protocol, false), output = Unpooled.buffer();
			try {
				StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
				ItemStack original = codec.decode(input);
				ItemStack edited = original.copy();
				edited.setEnchantments(ItemEnchantments.of(Collections.singletonMap(SHARPNESS, 9)));
				edited.setSkullOwnerName("Steve");
				assertEquals(Optional.of("Steve"), edited.skullOwnerName());
				CompoundTag detached = edited.nbt();
				assertEquals("Steve", detached.getCompoundTag("SkullOwner").getString("Name"));
				detached.getCompoundTag("SkullOwner").clear();
				detached.putString("Uninterpreted", "changed");
				codec.encode(output, edited);
				ItemStack decoded = codec.decode(output);
				assertEquals(9, decoded.enchantments().enchantmentLevel(SHARPNESS));
				assertEquals(Optional.of("Steve"), decoded.skullOwnerName());
				assertEquals("texture", decoded.nbt().getCompoundTag("SkullOwner").getString("Properties"));
				assertEquals("custom-data", decoded.nbt().getString("Uninterpreted"));
				assertEquals(5, original.enchantments().enchantmentLevel(SHARPNESS));
				assertEquals(Optional.of("Alex"), original.skullOwnerName());
				output.clear();
				edited.setSkullOwnerName(null);
				assertEquals(Optional.empty(), edited.skullOwnerName());
				assertNull(edited.nbt().getCompoundTag("SkullOwner").get("Name"));
				edited.setEnchantments(ItemEnchantments.EMPTY);
				codec.encode(output, edited);
				decoded = codec.decode(output);
				assertEquals(Optional.empty(), decoded.skullOwnerName());
				assertFalse(decoded.enchantments().hasEnchantments());
				assertEquals("texture", decoded.nbt().getCompoundTag("SkullOwner").getString("Properties"));
			} finally {
				input.release();
				output.release();
			}
		}
	}

	@Test
	void truncatedStacksRollbackAndCannotBeMistakenForEmptyStacks() throws IOException {
		for (int protocol : new int[]{47, 393, 401, 404, 754}) {
			StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
			ByteBuf input = fixture(protocol, false);
			try {
				int length = input.writerIndex();
				for (int end = 0; end < length; end++) {
					input.writerIndex(end);
					assertThrows(RuntimeException.class, () -> codec.decode(input));
					assertEquals(0, input.readerIndex());
				}
			} finally {
				input.release();
			}
		}
	}

	@Test
	void nullAndEmptyCompoundsStayDistinctAndEmptyStacksUseTheRightMarker() {
		for (int protocol : new int[]{47, 393, 401, 404, 754}) {
			StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				codec.encode(output, null);
				assertEquals(protocol < 404 ? 2 : 1, output.readableBytes());
				assertNull(codec.decode(output));
				for (boolean compound : new boolean[]{false, true}) {
					input.clear();
					output.clear();
					writeItemHeader(input, protocol, "stone", 1);
					if (compound) input.writeByte(10).writeShort(0).writeByte(0);
					else input.writeByte(0);
					ItemStack stack = codec.decode(input);
					assertEquals(compound, stack.nbt() != null);
					assertEquals(Optional.empty(), stack.skullOwnerName());
					codec.encode(output, stack);
					assertEquals(input.readerIndex(0), output);
				}
			} finally {
				input.release();
				output.release();
			}
		}
	}

	@Test
	void invalidEncodingRollsBackAndDoesNotSilentlyConvertBetweenProtocols() throws IOException {
		StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(47);
		ByteBuf input = fixture(47, false), output = Unpooled.buffer();
		try {
			output.writeByte(42);
			ItemStack stack = codec.decode(input);
			stack.setAmount(128);
			assertThrows(IllegalArgumentException.class, () -> codec.encode(output, stack));
			assertEquals(1, output.writerIndex());
			stack.setAmount(1);
			assertThrows(IllegalArgumentException.class, () -> ItemStack.codecFor(107).encode(output, stack));
			assertEquals(1, output.writerIndex());
			stack.setType(MinecraftKey.withDefaultNamespace("netherite_sword"));
			assertThrows(IllegalArgumentException.class, () -> codec.encode(output, stack));
			assertEquals(1, output.writerIndex());
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void encodingAcrossProtocolsRequiresMatchingRegistriesAndItemHeaders() {
		MinecraftKey stone = MinecraftKey.withDefaultNamespace("stone");
		ByteBuf output = Unpooled.buffer();
		try {
			ItemStack stack = new ItemStack(stone, 1, 107);
			StreamCodec<ByteBuf, ByteBuf, ItemStack> compatible = ItemStack.codecFor(108);
			compatible.encode(output, stack);
			assertEquals(stone, compatible.decode(output).type());
			assertFalse(output.isReadable());

			ItemStack shortIdStack = new ItemStack(stone, 1, 401);
			int start = output.writerIndex();
			assertThrows(IllegalArgumentException.class, () -> ItemStack.codecFor(404).encode(output, shortIdStack));
			assertEquals(start, output.writerIndex());
		} finally {
			output.release();
		}
	}

	@Test
	void flattenedCodecsRejectLegacyDamageAndNegativePresentIdsWithoutPartialIo() {
		for (int protocol : new int[]{393, 401, 404, 754}) {
			StreamCodec<ByteBuf, ByteBuf, ItemStack> codec = ItemStack.codecFor(protocol);
			ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
			try {
				ItemStack stack = new ItemStack(MinecraftKey.withDefaultNamespace("stone"), 1, protocol);
				stack.setLegacyData((short) 0);
				output.writeByte(42);
				assertThrows(IllegalArgumentException.class, () -> codec.encode(output, stack));
				assertEquals(1, output.writerIndex());
				assertEquals(42, output.getByte(0));
				if (protocol >= 404) {
					input.writeByte(42).writeBoolean(true);
					VARINT.encode(input, -1);
					input.writeByte(1).writeByte(0).readerIndex(1);
					assertThrows(IllegalArgumentException.class, () -> codec.decode(input));
					assertEquals(1, input.readerIndex());
				}
			} finally {
				input.release();
				output.release();
			}
		}
	}

	private static void writeItemHeader(ByteBuf input, int protocol, String type, int amount) {
		int id = ItemRegistry.forProtocol(protocol).idOf(MinecraftKey.withDefaultNamespace(type));
		if (protocol < 404) input.writeShort(id);
		else { input.writeBoolean(true); VARINT.encode(input, id); }
		input.writeByte(amount);
		if (protocol < 393) input.writeShort(123);
	}

	/** Independent named-NBT fixture using DataOutput, following the client PacketBuffer layout. */
	private static ByteBuf fixture(int protocol, boolean book) throws IOException {
		return fixture(protocol, book, "original-root");
	}

	private static ByteBuf fixture(int protocol, boolean book, String rootName) throws IOException {
		ByteBuf input = Unpooled.buffer();
		writeItemHeader(input, protocol, book ? "enchanted_book" : "diamond_sword", 3);
		DataOutputStream data = new DataOutputStream(new ByteBufOutputStream(input));
		data.writeByte(10);
		data.writeUTF(rootName);
		data.writeByte(8); data.writeUTF("Uninterpreted"); data.writeUTF("custom-data");
		data.writeByte(9); data.writeUTF(book ? "StoredEnchantments" : protocol < 393 ? "ench" : "Enchantments");
		data.writeByte(10); data.writeInt(1);
		data.writeByte(protocol < 393 ? 2 : 8); data.writeUTF("id");
		if (protocol < 393) data.writeShort(16); else data.writeUTF("minecraft:sharpness");
		data.writeByte(2); data.writeUTF("lvl"); data.writeShort(5);
		data.writeByte(0);
		data.writeByte(10); data.writeUTF("SkullOwner");
		data.writeByte(8); data.writeUTF("Name"); data.writeUTF("Alex");
		data.writeByte(8); data.writeUTF("Properties"); data.writeUTF("texture");
		data.writeByte(0);
		data.writeByte(0);
		return input;
	}
}

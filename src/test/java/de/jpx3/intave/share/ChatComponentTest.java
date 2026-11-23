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

package de.jpx3.intave.share;

import com.google.gson.JsonParser;
import com.viaversion.nbt.tag.ByteTag;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.TRUSTED_TAG;
import static org.junit.jupiter.api.Assertions.*;

final class ChatComponentTest {
	@Test
	void readsStyledMixedNbtListsAndPreservesTheOriginalEncoding() {
		CompoundTag root = new CompoundTag();
		root.putString("text", "Root");
		root.putString("color", "gold");
		root.put("bold", new ByteTag((byte) 1));
		CompoundTag wrapped = new CompoundTag();
		wrapped.put("", new StringTag(" plain"));
		CompoundTag styled = new CompoundTag();
		styled.putString("text", " styled");
		styled.put("bold", new ByteTag((byte) 0));
		styled.put("italic", new ByteTag((byte) 1));
		ListTag<CompoundTag> extra = new ListTag<>(CompoundTag.class);
		extra.add(wrapped);
		extra.add(styled);
		root.put("extra", extra);

		ByteBuf input = Unpooled.buffer(), output = Unpooled.buffer();
		try {
			TRUSTED_TAG.encode(input, root);
			ChatComponent component = ChatComponent.NBT_CODEC.decode(input.duplicate());
			var json = new JsonParser().parse(component.json()).getAsJsonObject();
			assertTrue(json.get("bold").getAsBoolean());
			assertEquals(" plain", json.getAsJsonArray("extra").get(0).getAsString());
			assertFalse(json.getAsJsonArray("extra").get(1).getAsJsonObject().get("bold").getAsBoolean());
			assertEquals("\u00a76\u00a7lRoot\u00a76\u00a7l plain\u00a76\u00a7o styled", component.legacyText());
			ChatComponent.NBT_CODEC.encode(output, component);
			assertEquals(input, output);
		} finally {
			input.release();
			output.release();
		}
	}

	@Test
	void convertsEditedJsonToNbtWithoutLosingNestedTextAndStyles() {
		String json = "{\"text\":\"Root\",\"bold\":true,\"extra\":[\" plain\",{\"text\":\" styled\",\"italic\":true,\"bold\":false}]}";
		ChatComponent component = ChatComponent.json(json);
		ByteBuf buffer = Unpooled.buffer();
		try {
			ChatComponent.NBT_CODEC.encode(buffer, component);
			ChatComponent decoded = ChatComponent.NBT_CODEC.decode(buffer);
			assertFalse(buffer.isReadable());
			assertEquals(new JsonParser().parse(json), new JsonParser().parse(decoded.json()));
			assertEquals(component.legacyText(), decoded.legacyText());
		} finally {
			buffer.release();
		}
	}

	@Test
	void retainsLegacyFormattingAndOwnsItsNbtSnapshot() {
		assertEquals("[\u00a7aTeam]", ChatComponent.legacy("[\u00a7aTeam]").legacyText());
		CompoundTag tag = new CompoundTag();
		tag.putString("text", "Original");
		ChatComponent component = ChatComponent.nbt(tag);
		tag.putString("text", "Changed");
		assertEquals("\u00a7fOriginal", component.legacyText());
	}
}

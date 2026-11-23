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

import com.google.gson.*;
import com.viaversion.nbt.tag.*;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Retains the original component representation when a packet is re-encoded. */
public final class ChatComponent {
	public static final StreamCodec<ByteBuf, ByteBuf, ChatComponent> LEGACY_CODEC =
		ByteBufStreamCodecs.VARINT_STRING.beforeAndAfter(ChatComponent::legacy, ChatComponent::legacyText);
	public static final StreamCodec<ByteBuf, ByteBuf, ChatComponent> JSON_CODEC =
		ByteBufStreamCodecs.stringCodec(262144).beforeAndAfter(ChatComponent::json, ChatComponent::json);
	public static final StreamCodec<ByteBuf, ByteBuf, ChatComponent> NBT_CODEC =
		ByteBufStreamCodecs.TRUSTED_TAG.beforeAndAfter(ChatComponent::nbt, ChatComponent::nbt);

	private final String legacy;
	private final String json;
	private final Tag nbt;

	private ChatComponent(String legacy, String json, Tag nbt) {
		this.legacy = legacy;
		this.json = json;
		this.nbt = nbt;
	}

	public static ChatComponent legacy(String text) {
		return new ChatComponent(Objects.requireNonNull(text), null, null);
	}

	public static ChatComponent json(String json) {
		return new ChatComponent(null, Objects.requireNonNull(json), null);
	}

	public static ChatComponent nbt(Tag tag) {
		return new ChatComponent(null, null, tag.copy());
	}

	public String json() {
		if (json != null) return json;
		return legacy != null ? new JsonPrimitive(legacy).toString() : toJson(nbt).toString();
	}

	public String legacyText() {
		return legacy != null ? legacy : BaseComponent.toLegacyText(ComponentSerializer.parse(json()));
	}

	private Tag nbt() {
		return nbt != null ? nbt : fromJson(new JsonParser().parse(json()));
	}

	private static JsonElement toJson(Tag tag) {
		if (tag instanceof StringTag) return new JsonPrimitive(((StringTag) tag).getValue());
		if (tag instanceof NumberTag) return new JsonPrimitive((Number) tag.getValue());
		if (tag instanceof CompoundTag) {
			JsonObject result = new JsonObject();
			for (Map.Entry<String, Tag> entry : ((CompoundTag) tag).entrySet()) {
				Tag value = entry.getValue();
				String key = entry.getKey();
				boolean styleFlag = key.equals("bold") || key.equals("italic") || key.equals("underlined")
					|| key.equals("strikethrough") || key.equals("obfuscated") || key.equals("interpret");
				result.add(key, styleFlag && value instanceof NumberTag
					? new JsonPrimitive(((NumberTag) value).asByte() != 0) : toJson(value));
			}
			return result;
		}
		JsonArray result = new JsonArray();
		if (tag instanceof ListTag) {
			for (Tag element : (ListTag<?>) tag) {
				// NbtOps represents heterogeneous lists using compounds with an empty-key wrapper.
				if (element instanceof CompoundTag && ((CompoundTag) element).size() == 1
					&& ((CompoundTag) element).contains("")) element = ((CompoundTag) element).get("");
				result.add(toJson(element));
			}
		} else if (tag instanceof IntArrayTag) {
			for (int value : ((IntArrayTag) tag).getValue()) result.add(new JsonPrimitive(value));
		} else if (tag instanceof LongArrayTag) {
			for (long value : ((LongArrayTag) tag).getValue()) result.add(new JsonPrimitive(value));
		} else if (tag instanceof ByteArrayTag) {
			for (byte value : ((ByteArrayTag) tag).getValue()) result.add(new JsonPrimitive(value));
		} else {
			throw new IllegalArgumentException("Unsupported component tag " + tag);
		}
		return result;
	}

	private static Tag fromJson(JsonElement json) {
		if (json.isJsonObject()) {
			CompoundTag tag = new CompoundTag();
			for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
				if (!entry.getValue().isJsonNull()) tag.put(entry.getKey(), fromJson(entry.getValue()));
			}
			return tag;
		}
		if (json.isJsonArray()) {
			List<Tag> values = new ArrayList<>();
			for (JsonElement element : json.getAsJsonArray()) values.add(fromJson(element));
			boolean mixed = values.stream().map(Tag::getTagId).distinct().count() > 1;
			if (mixed) {
				for (int i = 0; i < values.size(); i++) {
					Tag value = values.get(i);
					if (value instanceof CompoundTag && !((CompoundTag) value).contains("")) continue;
					CompoundTag wrapper = new CompoundTag();
					wrapper.put("", value);
					values.set(i, wrapper);
				}
			}
			return new ListTag<>(values);
		}
		JsonPrimitive primitive = json.getAsJsonPrimitive();
		if (primitive.isBoolean()) return new ByteTag((byte) (primitive.getAsBoolean() ? 1 : 0));
		if (primitive.isNumber()) return new DoubleTag(primitive.getAsDouble());
		return new StringTag(primitive.getAsString());
	}
}

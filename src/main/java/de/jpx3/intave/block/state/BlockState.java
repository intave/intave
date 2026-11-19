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

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.MinecraftKey;

import java.util.Collections;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

public final class BlockState {
	private static final StreamCodec<JsonReader, JsonWriter, Map<String, String>> PROPERTIES = stringMapCodec(STRING);
	static final StreamCodec<JsonReader, JsonWriter, BlockState> JSON_CODEC = tuple(
		(writer, state) -> {
			writer.value(state.id).value(state.nameKey.fullKey());
			PROPERTIES.encode(writer, state.properties);
			writer.value(state.legacyVariantId);
		},
		reader -> new BlockState(
			reader.nextInt(), MinecraftKey.from(reader.nextString()),
			PROPERTIES.decode(reader), reader.nextInt()
		)
	);

	private final int id;
	private final MinecraftKey nameKey;
	private final Map<String, String> properties;
	private final int legacyVariantId;

	BlockState(int id, MinecraftKey nameKey, Map<String, String> properties, int legacyVariantId) {
		this.id = id;
		this.nameKey = nameKey;
		this.properties = Collections.unmodifiableMap(new TreeMap<>(properties));
		this.legacyVariantId = legacyVariantId;
	}

	public int id() {
		return id;
	}

	public String name() {
		return nameKey.fullKey();
	}

	public MinecraftKey nameKey() {
		return nameKey;
	}

	public Map<String, String> properties() {
		return properties;
	}

	public boolean legacy() {
		return legacyVariantId >= 0;
	}

	public int legacyVariantId() {
		return legacyVariantId;
	}

	@Override
	public String toString() {
		if (properties.isEmpty()) {
			return nameKey.toString();
		}
		StringJoiner joined = new StringJoiner(",", nameKey + "[", "]");
		properties.forEach((key, value) -> joined.add(key + "=" + value));
		return joined.toString();
	}
}

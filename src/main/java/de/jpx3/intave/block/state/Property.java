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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

final class Property {
	static final StreamCodec<JsonReader, JsonWriter, List<String>> VALUES = listCodecOf(STRING);
	static final StreamCodec<JsonReader, JsonWriter, Property> JSON_CODEC = tuple(
		(writer, property) -> {
			writer.value(property.name);
			VALUES.encode(writer, property.values);
		},
		reader -> new Property(reader.nextString(), VALUES.decode(reader))
	);

	private final String name;
	private final List<String> values;

	private Property(String name, List<String> values) {
		if (values.isEmpty()) throw new IllegalStateException("Empty property domain: " + name);
		this.name = name;
		this.values = values;
	}

	public String name() {
		return name;
	}

	public List<String> values() {
		return values;
	}

	Map<String, Integer> offsets(String block, int stride) {
		Map<String, Integer> offsets = new HashMap<>();
		for (int i = 0; i < values.size(); i++) {
			String value = LegacyProperties.value(block, name, values.get(i));
			if (offsets.put(value, Math.multiplyExact(i, stride)) != null)
				throw new IllegalStateException("Duplicate value for " + block + "[" + name + "]");
		}
		return offsets;
	}
}

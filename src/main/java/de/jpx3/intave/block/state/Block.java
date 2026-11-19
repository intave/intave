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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.jpx3.intave.codec.JsonStreamCodecs.listCodecOf;
import static de.jpx3.intave.codec.JsonStreamCodecs.tuple;

public final class Block {
	private static final StreamCodec<JsonReader, JsonWriter, List<Property>> PROPERTIES = listCodecOf(Property.JSON_CODEC);
	static final StreamCodec<JsonReader, JsonWriter, Block> JSON_CODEC = tuple(
		(writer, block) -> {
			writer.value(block.start).value(block.nameKey.fullKey());
			PROPERTIES.encode(writer, block.properties);
			writer.value(block.defaultId);
		},
		reader -> new Block(reader.nextInt(), reader.nextString(), PROPERTIES.decode(reader), reader.nextInt())
	);

	final int start, end, defaultId;
	private final MinecraftKey nameKey;
	private final List<Property> properties;
	private final Map<String, Map<String, Integer>> offsets = new HashMap<>();

	private Block(int start, String nameKey, List<Property> properties, int defaultId) {
		this.start = start;
		this.nameKey = MinecraftKey.from(nameKey);
		this.properties = properties;
		this.defaultId = defaultId;
		int stride = 1;
		for (int i = properties.size() - 1; i >= 0; i--) {
			Property property = properties.get(i);
			if (offsets.put(property.name(), property.offsets(nameKey, stride)) != null)
				throw new IllegalStateException("Duplicate block property: " + property.name());
			stride = Math.multiplyExact(stride, property.values().size());
		}
		end = Math.addExact(start, stride);
		if (defaultId < start || defaultId >= end) throw new IllegalStateException("Invalid default block state");
	}

	public String name() {
		return nameKey.fullKey();
	}

	public MinecraftKey nameKey() {
		return nameKey;
	}

	BlockState stateOf(int id) {
		if (id < start || id >= end) {
			throw new IllegalArgumentException("Invalid block state ID: " + id);
		}
		int stride = end - start;
		Map<String, String> values = new HashMap<>();
		for (Property property : properties) {
			List<String> properties = property.values();
			stride /= properties.size();
			values.put(property.name(), properties.get((id - start) / stride % properties.size()));
		}
		return new BlockState(id, nameKey, values, -1);
	}

	BlockState stateOf(BlockState def, Map<String, String> supplied) {
		int id = def.id();
		for (Map.Entry<String, String> property : supplied.entrySet()) {
			String key = property.getKey();
			Map<String, Integer> values = offsets.get(key);
			if (values == null) {
				if (key.equals("waterlogged") && property.getValue().equals("false")) continue;
				return null;
			}
			String previous = LegacyProperties.value(name(), key, def.properties().get(key));
			Integer offset = values.get(property.getValue());
			if (previous == null || offset == null) {
				return null;
			}
			id += offset - values.get(previous);
		}
		return id == def.id() ? def : stateOf(id);
	}

	int[] defaultIds() {
		// Old cauldrons have separate defaults for the empty and filled canonical block names.
		return name().equals("minecraft:cauldron") && offsets.containsKey("level")
			? new int[]{start, start + 1} : new int[]{defaultId};
	}
}

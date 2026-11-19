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
import de.jpx3.intave.resource.Resources;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

final class Index {
	static final StreamCodec<JsonReader, JsonWriter, Index> JSON_CODEC = object(
		integerField("schemaVersion", (Index index) -> 1),
		field("protocols", stringMapCodec(STRING), index -> index.protocols, null),
		field("sources", stringMapCodec(stringMapCodec(JSON_PRIMITIVE)), index -> index.sources, Collections.emptyMap()),
		Index::new
	);

	private static final String DIRECTORY = "registry/block_states/";
	private final static Index theIndex = read("index.json", Index.JSON_CODEC);

	private final Map<String, String> protocols;
	private final Map<String, Map<String, Comparable<?>>> sources;

	private Index(int schemaVersion, Map<String, String> protocols, Map<String, Map<String, Comparable<?>>> sources) {
		if (schemaVersion != 1) {
			throw new IllegalStateException("Unsupported block-state index schema: " + schemaVersion);
		}
		Objects.requireNonNull(protocols, "Missing protocols in block-state index");
		this.protocols = Collections.unmodifiableMap(new LinkedHashMap<>(protocols));
		Map<String, Map<String, Comparable<?>>> sourceCopies = new LinkedHashMap<>();
		sources.forEach((version, metadata) ->
			sourceCopies.put(version, Collections.unmodifiableMap(new LinkedHashMap<>(metadata))));
		this.sources = Collections.unmodifiableMap(sourceCopies);
	}

	public Map<String, String> protocols() {
		return protocols;
	}

	public Map<String, Map<String, Comparable<?>>> sources() {
		return sources;
	}

	public static Index get() {
		return theIndex;
	}

	private static <T> T read(String file, StreamCodec<JsonReader, JsonWriter, T> codec) {
		return decodeFromString(codec, Resources.resourceFromJarOrBuild(DIRECTORY + file).readAsString());
	}
}

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

package de.jpx3.intave.registry;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.resource.Resources;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

/**
 * Loads tables from {@code registry/<name>/}. The index maps each supported wire
 * protocol to a numeric JSON filename. Protocols sharing a table share its instance.
 */
public final class ProtocolRegistryFiles<R> {
	private final String directory;
	private final Map<Integer, Integer> tableByProtocol;
	private final Map<Integer, R> loadedTables = new ConcurrentHashMap<>();
	private final Function<Integer, R> load;

	public <T> ProtocolRegistryFiles(String name, StreamCodec<JsonReader, JsonWriter, T> codec, Function<T, R> create) {
		directory = "registry/" + name + "/";
		Map<Integer, Integer> index = new LinkedHashMap<>();
		read("index.json", stringMapCodec(INTEGER)).forEach((protocol, table) -> index.put(Integer.parseInt(protocol), table));
		tableByProtocol = Collections.unmodifiableMap(index);
		load = table -> create.apply(read(table + ".json", codec));
	}

	public R forProtocol(int protocol) {
		Integer table = tableByProtocol.get(protocol);
		if (table == null) {
			throw new IllegalArgumentException("Unsupported protocol " + protocol + " for " + directory);
		}
		return loadedTables.computeIfAbsent(table, load);
	}

	public Set<Integer> supportedProtocolVersions() {
		return tableByProtocol.keySet();
	}

	private <T> T read(String file, StreamCodec<JsonReader, JsonWriter, T> codec) {
		return decodeFromString(codec, Resources.resourceFromJarOrBuild(directory + file).readAsString());
	}
}

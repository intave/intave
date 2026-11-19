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
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.resource.Resources;
import de.jpx3.intave.share.MaterialAndVariant;

import java.io.IOException;
import java.util.*;

import static de.jpx3.intave.codec.JsonStreamCodecs.*;

public final class BlockStates {
	private static final String DIRECTORY = "registry/block_states/";
	private static final StreamCodec<JsonReader, JsonWriter, List<BlockState>> STATES = listCodecOf(BlockState.JSON_CODEC);
	private static final StreamCodec<JsonReader, JsonWriter, List<Block>> BLOCKS = listCodecOf(Block.JSON_CODEC);
	static final StreamCodec<JsonReader, JsonWriter, BlockStates> JSON_CODEC = of(
		(writer, registry) -> {
			writer.beginObject().name("schemaVersion").value(1).name("legacy").value(registry.legacy);
			if (registry.legacy) {
				STATES.encode(writer.name("states"), new ArrayList<>(registry.legacyStates.values()));
			} else {
				BLOCKS.encode(writer.name("blocks"), new ArrayList<>(registry.blocks.values()));
			}
			writer.endObject();
		},
		BlockStates::read
	);

	private final boolean legacy;
	private final Map<Integer, BlockState> legacyStates = new TreeMap<>();
	private final NavigableMap<Integer, Block> blocks = new TreeMap<>();
	private final Map<String, List<BlockState>> defaultsByName;

	private BlockStates(boolean legacy, List<BlockState> states, List<Block> blocks) {
		this.legacy = legacy;
		if (legacy) {
			for (BlockState state : states) {
				int id = state.id();
				if (id < 0 || id >= 4096 || legacyStates.put(id, state) != null) {
					throw new IllegalStateException("Invalid legacy registry ID: " + id);
				}
			}
		} else {
			int end = 0;
			for (Block block : blocks) {
				if (block.start != end) {
					throw new IllegalStateException("Noncontiguous block registry at " + block.start);
				}
				this.blocks.put(block.start, block);
				end = block.end;
			}
		}
		defaultsByName = indexDefaults();
	}

	public static Set<Integer> supportedProtocolVersions() {
		return Registries.BY_PROTOCOL.keySet();
	}

	public static BlockState stateOf(int protocolVersion, int stateId) {
		BlockState state = registryFor(protocolVersion).stateOf(stateId);
		if (state == null)
			throw new IllegalArgumentException("Unknown block state " + stateId + " for protocol " + protocolVersion);
		return state;
	}


	public static Optional<MaterialAndVariant> materialAndVariantOf(int protocolVersion, int stateId) {
		return Resolution.resolve(protocolVersion, stateId);
	}

	public static void invalidateMaterialAndVariantCache() {
		Resolution.invalidateCache();
	}

	/**
	 * Translates by block identity and properties, never by numeric ID similarity.
	 * Added properties use the target block's actual default; unsupported values fail explicitly.
	 * Legacy targets project onto native metadata: neighbor-derived and block-entity
	 * properties are supplied by the world, not reconstructed by this state-only API.
	 */
	public static BlockState translate(int sourceProtocol, int stateId, int targetProtocol) {
		BlockState source = stateOf(sourceProtocol, stateId);
		if (sourceProtocol == targetProtocol) {
			return source;
		}
		BlockStates target = registryFor(targetProtocol);
		String name = LegacyProperties.nameForTarget(source, sourceProtocol, targetProtocol);
		if (source.legacy() && target.legacy) {
			BlockState candidate = target.stateOf(stateId);
			if (candidate != null && LegacyProperties.name(candidate).equals(name))
				return candidate;
		}
		BlockState result = name == null ? null : target.find(name, LegacyProperties.propertiesForTarget(source, targetProtocol));
		if (result == null)
			throw new UnsupportedOperationException("Block state " + source + " from protocol " + sourceProtocol
				+ " cannot be represented in protocol " + targetProtocol);
		return result;
	}

	static BlockStates registryFor(int protocol) {
		BlockStates registry = Registries.BY_PROTOCOL.get(protocol);
		if (registry == null) {
			throw new IllegalArgumentException("Unsupported block-state protocol: " + protocol);
		}
		return registry;
	}

	private static Map<Integer, BlockStates> loadRegistries() {
		IntaveLogger logger = IntaveLogger.logger();
		if (logger != null) logger.info("Loading block-state registries from " + DIRECTORY);
		Index index = Index.get();
		Map<String, BlockStates> byVersion = new HashMap<>();
		Map<Integer, BlockStates> byProtocol = new LinkedHashMap<>();
		index.protocols().forEach((protocol, version) -> {
			BlockStates registry = byVersion.computeIfAbsent(version, BlockStates::loadRegistry);
			byProtocol.put(Integer.parseInt(protocol), registry);
		});
		if (logger != null) logger.info("Loaded " + byVersion.size() + " block-state registries for " + byProtocol.size() + " protocols");
		return Collections.unmodifiableMap(byProtocol);
	}

	private static BlockStates loadRegistry(String version) {
		String json = Resources.resourceFromJarOrBuild(DIRECTORY + version + ".json").readAsString();
		return decodeFromString(JSON_CODEC, json);
	}

	private static BlockStates read(JsonReader reader) throws IOException {
		int version = 0;
		Boolean legacy = null;
		List<BlockState> states = null;
		List<Block> blocks = null;
		reader.beginObject();
		while (reader.hasNext()) {
			switch (reader.nextName()) {
				case "schemaVersion": version = reader.nextInt(); break;
				case "legacy": legacy = reader.nextBoolean(); break;
				case "states": states = STATES.decode(reader); break;
				case "blocks": blocks = BLOCKS.decode(reader); break;
				default: reader.skipValue();
			}
		}
		reader.endObject();
		if (version != 1) {
			throw new IllegalStateException("Unsupported block-state table schema: " + version);
		}
		if (legacy == null || (legacy ? states == null : blocks == null)) {
			throw new IllegalStateException("Missing block-state table data");
		}
		return new BlockStates(legacy, states, blocks);
	}

	private BlockState stateOf(int id) {
		if (legacy) {
			return legacyStates.get(id);
		}
		Map.Entry<Integer, Block> entry = blocks.floorEntry(id);
		if (entry == null || id >= entry.getValue().end) {
			return null;
		}
		return entry.getValue().stateOf(id);
	}

	BlockState find(String name, Map<String, String> properties) {
		List<BlockState> candidates = defaultsByName.getOrDefault(name, Collections.emptyList());
		if (legacy) {
			Map<String, String> wanted = new HashMap<>(properties);
			wanted.remove("waterlogged", "false");
			LegacyProperties.removeDerivedLegacyProperties(name, wanted);
			for (BlockState candidate : candidates) {
				Map<String, String> actual = LegacyProperties.properties(candidate);
				actual.remove("waterlogged", "false");
				LegacyProperties.removeDerivedLegacyProperties(name, actual);
				if (wanted.equals(actual)) {
					return candidate;
				}
			}
		} else {
			for (BlockState defaults : candidates) {
				Block block = blocks.floorEntry(defaults.id()).getValue();
				BlockState match = block.stateOf(defaults, properties);
				if (match != null) {
					return match;
				}
			}
		}
		return null;
	}

	private Map<String, List<BlockState>> indexDefaults() {
		List<BlockState> candidates = new ArrayList<>(legacyStates.values());
		for (Block block : blocks.values()) {
			for (int id : block.defaultIds()) {
				candidates.add(block.stateOf(id));
			}
		}
		Map<String, List<BlockState>> index = new HashMap<>();
		for (BlockState candidate : candidates) {
			String name = LegacyProperties.name(candidate);
			index.computeIfAbsent(name, key -> new ArrayList<>()).add(candidate);
		}
		return index;
	}

	private static final class Registries {
		private static final Map<Integer, BlockStates> BY_PROTOCOL = loadRegistries();
	}
}


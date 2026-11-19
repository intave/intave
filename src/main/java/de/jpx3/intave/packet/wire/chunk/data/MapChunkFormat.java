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

package de.jpx3.intave.packet.wire.chunk.data;

/** Packet and section format boundaries from the matching client sources. */
enum MapChunkFormat {
	LEGACY_MASK(47, 107),
	VARINT_MASK(107, 110),
	BLOCK_ENTITIES(110, 393),
	FLATTENED(393, 477),
	HEIGHTMAPS(477, 573),
	BIOMES(573, 735),
	IGNORE_OLD_DATA(735, 751),
	VARINT_BIOMES(751, 755),
	BITSET_MASK(755, 757),
	LIGHT(757, 763),
	WITHOUT_TRUST_EDGES(763, 764),
	UNNAMED_NBT(764, 770),
	REGISTRY_HEIGHTMAPS(770, 775),
	FLUID_COUNTS(775, Integer.MAX_VALUE);

	final int minimumProtocol, maximumProtocol;
	final boolean fullChunk, ignoreOldData, heightmapTag, namedNbt, blockEntities, packedBlockEntities, light, trustEdges;
	// Mask width: 16 or 32 for scalar masks, -1 for a long array, 0 for no mask.
	final int maskWidth;
	// Biomes: 1024 fixed-width ints, -1 for a VarInt array, 0 for in-section biomes.
	final int biomeFormat;

	MapChunkFormat(int minimumProtocol, int maximumProtocol) {
		this.minimumProtocol = minimumProtocol;
		this.maximumProtocol = maximumProtocol;
		fullChunk = minimumProtocol < 755;
		ignoreOldData = minimumProtocol == 735;
		maskWidth = minimumProtocol < 107 ? 16 : minimumProtocol < 755 ? 32 : minimumProtocol < 757 ? -1 : 0;
		heightmapTag = minimumProtocol >= 477 && minimumProtocol < 770;
		namedNbt = minimumProtocol < 764;
		biomeFormat = minimumProtocol >= 573 && minimumProtocol < 751 ? 1024
			: minimumProtocol >= 751 && minimumProtocol < 757 ? -1 : 0;
		blockEntities = minimumProtocol >= 110;
		packedBlockEntities = minimumProtocol >= 757;
		light = minimumProtocol >= 757;
		trustEdges = minimumProtocol >= 757 && minimumProtocol < 763;
	}
}

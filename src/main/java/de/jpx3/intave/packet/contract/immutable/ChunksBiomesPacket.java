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

package de.jpx3.intave.packet.contract.immutable;

import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.PacketContract;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.share.ChunkBiomeData;

import java.util.List;

import static de.jpx3.intave.packet.ConnectionState.PLAY;

public interface ChunksBiomesPacket extends PacketContract, ChunkAddressed {
	List<ChunkBiomeData> chunks();

	@Override
	default boolean appliesTo(ConnectionState state, PacketType type) {
		return state == PLAY && type == PacketType.Server.CHUNKS_BIOMES;
	}
}

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

package de.jpx3.intave.packet.wire.player.inforemove;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerInfoRemovePacket;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.jpx3.intave.packet.wire.WireCodecs.count;

public class PlayerInfoRemoveWirePacket implements MutablePlayerInfoRemovePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerInfoRemoveWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.playerIds.size());
						for (UUID id : packet.playerIds) {
							output.writeLong(id.getMostSignificantBits());
							output.writeLong(id.getLeastSignificantBits());
						}
					},
					input -> {
						int count = count(input, input.readableBytes() / 16);
						List<UUID> playerIds = new ArrayList<>(count);
						for (int i = 0; i < count; i++)
							playerIds.add(new UUID(input.readLong(), input.readLong()));
						return new PlayerInfoRemoveWirePacket(playerIds);
					});

	protected List<UUID> playerIds = new ArrayList<>();

	public PlayerInfoRemoveWirePacket() {
		this(new ArrayList<>());
	}

	public PlayerInfoRemoveWirePacket(List<UUID> playerIds) {
		this.playerIds = playerIds;
	}

	@Override
	public List<UUID> playerIds() {
		return this.playerIds;
	}

	@Override
	public void setPlayerIds(List<UUID> value) {
		this.playerIds = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("PLAYER_INFO_REMOVE".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 761;
	}
}

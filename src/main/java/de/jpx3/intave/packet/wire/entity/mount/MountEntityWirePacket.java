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

package de.jpx3.intave.packet.wire.entity.mount;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutableMountEntityPacket;
import io.netty.buffer.ByteBuf;

import static de.jpx3.intave.packet.wire.WireCodecs.count;

public class MountEntityWirePacket implements MutableMountEntityPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, MountEntityWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
						ByteBufStreamCodecs.VARINT.encode(output, packet.passengerIds.length);
						for (int passenger : packet.passengerIds) ByteBufStreamCodecs.VARINT.encode(output, passenger);
					},
					input -> {
						int entityId = ByteBufStreamCodecs.VARINT.decode(input);
						int[] passengerIds = new int[count(input, input.readableBytes())];
						for (int i = 0; i < passengerIds.length; i++)
							passengerIds[i] = ByteBufStreamCodecs.VARINT.decode(input);
						return new MountEntityWirePacket(entityId, passengerIds);
					});

	protected int entityId = 0;

	protected int[] passengerIds = new int[0];

	public MountEntityWirePacket() {
		this(0, new int[0]);
	}

	public MountEntityWirePacket(int entityId, int[] passengerIds) {
		this.entityId = entityId;
		this.passengerIds = passengerIds;
	}

	@Override
	public int entityId() {
		return this.entityId;
	}

	@Override
	public void setEntityId(int value) {
		this.entityId = value;
	}

	@Override
	public int[] passengerIds() {
		return this.passengerIds;
	}

	@Override
	public void setPassengerIds(int[] value) {
		this.passengerIds = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("MOUNT".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 107;
	}
}

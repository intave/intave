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

package de.jpx3.intave.packet.wire.entity.destroy;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public final class EntityDestroySingleEntityWirePacket extends EntityDestroyWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityDestroySingleEntityWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								if (packet.entityIds.length != 1)
									throw new IllegalArgumentException(
											"1.17 removes one entity per packet");

								for (int id : packet.entityIds) ByteBufStreamCodecs.VARINT.encode(output, id);
							},
							input -> {
								int[] entityIds = new int[1];
								for (int i = 0; i < entityIds.length; i++)
									entityIds[i] = ByteBufStreamCodecs.VARINT.decode(input);
								return new EntityDestroySingleEntityWirePacket(entityIds);
							});

	public EntityDestroySingleEntityWirePacket() {
		super();
	}

	public EntityDestroySingleEntityWirePacket(int[] entityIds) {
		super(entityIds);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755 && protocolVersion < 756;
	}
}

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

import static de.jpx3.intave.packet.wire.WireCodecs.count;

public final class EntityDestroyLegacyArrayWirePacket extends EntityDestroyWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityDestroyLegacyArrayWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								ByteBufStreamCodecs.VARINT.encode(output, packet.entityIds.length);
								for (int id : packet.entityIds) ByteBufStreamCodecs.VARINT.encode(output, id);
							},
							input -> {
								int[] entityIds = new int[count(input, input.readableBytes())];
								for (int i = 0; i < entityIds.length; i++)
									entityIds[i] = ByteBufStreamCodecs.VARINT.decode(input);
								return new EntityDestroyLegacyArrayWirePacket(entityIds);
							});

	public EntityDestroyLegacyArrayWirePacket() {
		super();
	}

	public EntityDestroyLegacyArrayWirePacket(int[] entityIds) {
		super(entityIds);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 755;
	}
}

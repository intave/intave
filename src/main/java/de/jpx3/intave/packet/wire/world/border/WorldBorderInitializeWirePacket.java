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

package de.jpx3.intave.packet.wire.world.border;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.codec.VarLongCodec;
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

public final class WorldBorderInitializeWirePacket extends WorldBorderWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WorldBorderInitializeWirePacket>
			STREAM_CODEC =
					StreamCodec.of(
							(output, packet) -> {
								output.writeDouble(packet.center.getX());
								output.writeDouble(packet.center.getZ());
								output.writeDouble(packet.oldSize);
								output.writeDouble(packet.newSize);
								VarLongCodec.INSTANCE.encode(output, packet.lerpTime);
								ByteBufStreamCodecs.VARINT.encode(output, packet.absoluteMaxSize);
								ByteBufStreamCodecs.VARINT.encode(output, packet.warningDistance);
								ByteBufStreamCodecs.VARINT.encode(output, packet.warningDelay);
							},
							input -> {
								Position center =
										new Position(input.readDouble(), 0, input.readDouble());
								double oldSize = input.readDouble();
								double newSize = input.readDouble();
								long lerpTime = VarLongCodec.INSTANCE.decode(input);
								int absoluteMaxSize = ByteBufStreamCodecs.VARINT.decode(input);
								int warningDistance = ByteBufStreamCodecs.VARINT.decode(input);
								int warningDelay = ByteBufStreamCodecs.VARINT.decode(input);
								return new WorldBorderInitializeWirePacket(
										center,
										oldSize,
										newSize,
										lerpTime,
										absoluteMaxSize,
										warningDistance,
										warningDelay);
							});

	public WorldBorderInitializeWirePacket() {
		action = Action.INITIALIZE;
	}

	public WorldBorderInitializeWirePacket(
			Position center,
			double oldSize,
			double newSize,
			long lerpTime,
			int absoluteMaxSize,
			int warningDistance,
			int warningDelay) {
		this();
		this.center = center;
		this.oldSize = oldSize;
		this.newSize = newSize;
		this.lerpTime = lerpTime;
		this.absoluteMaxSize = absoluteMaxSize;
		this.warningDistance = warningDistance;
		this.warningDelay = warningDelay;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return state == ConnectionState.PLAY && "INITIALIZE_BORDER".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 755;
	}

	@Override
	public void setAction(Action value) {
		if (Objects.requireNonNull(value, "action") != Action.INITIALIZE)
			throw new IllegalArgumentException("Action is fixed for INITIALIZE_BORDER");
	}
}

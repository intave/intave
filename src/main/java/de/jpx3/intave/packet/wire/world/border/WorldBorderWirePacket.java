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
import de.jpx3.intave.packet.contract.mutable.MutableWorldBorderPacket;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

import static de.jpx3.intave.packet.wire.WireCodecs.enumValue;

/** The combined border packet used before 1.17. Its action is part of the payload. */
public class WorldBorderWirePacket implements MutableWorldBorderPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, WorldBorderWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.action.ordinal());
						if (packet.center() != null) {
							output.writeDouble(packet.center.getX());
							output.writeDouble(packet.center.getZ());
						}
						if (packet.oldSize() != null) output.writeDouble(packet.oldSize);
						if (packet.size() != null || packet.newSize() != null)
							output.writeDouble(packet.newSize);
						if (packet.lerpTime() != null)
							VarLongCodec.INSTANCE.encode(output, packet.lerpTime);
						if (packet.absoluteMaxSize() != null)
							ByteBufStreamCodecs.VARINT.encode(output, packet.absoluteMaxSize);
						if (packet.action == Action.INITIALIZE
								|| packet.action == Action.WARNING_DISTANCE)
							ByteBufStreamCodecs.VARINT.encode(output, packet.warningDistance);
						if (packet.action == Action.INITIALIZE
								|| packet.action == Action.WARNING_DELAY)
							ByteBufStreamCodecs.VARINT.encode(output, packet.warningDelay);
					},
					input -> {
						Action action = enumValue(Action.values(), ByteBufStreamCodecs.VARINT.decode(input));
						boolean initialize = action == Action.INITIALIZE;
						Position center =
								initialize || action == Action.SET_CENTER
										? new Position(input.readDouble(), 0, input.readDouble())
										: new Position();
						double oldSize =
								initialize || action == Action.LERP_SIZE
										? input.readDouble()
										: 60000000;
						double newSize =
								initialize
												|| action == Action.LERP_SIZE
												|| action == Action.SET_SIZE
										? input.readDouble()
										: 60000000;
						long lerpTime =
								initialize || action == Action.LERP_SIZE
										? VarLongCodec.INSTANCE.decode(input)
										: 0;
						int absoluteMaxSize = initialize ? ByteBufStreamCodecs.VARINT.decode(input) : 29999984;
						int warningDistance =
								initialize || action == Action.WARNING_DISTANCE ? ByteBufStreamCodecs.VARINT.decode(input) : 5;
						int warningDelay =
								initialize || action == Action.WARNING_DELAY ? ByteBufStreamCodecs.VARINT.decode(input) : 15;
						return new WorldBorderWirePacket(
								action,
								center,
								oldSize,
								newSize,
								lerpTime,
								absoluteMaxSize,
								warningDistance,
								warningDelay);
					});

	protected Action action = Action.INITIALIZE;

	protected Position center = new Position();

	protected double oldSize = 60000000, newSize = 60000000;

	protected long lerpTime;

	protected int absoluteMaxSize = 29999984, warningDistance = 5, warningDelay = 15;

	public WorldBorderWirePacket() {
		this(Action.INITIALIZE, new Position(), 60000000, 60000000, 0, 29999984, 5, 15);
	}

	public WorldBorderWirePacket(
			Action action,
			Position center,
			double oldSize,
			double newSize,
			long lerpTime,
			int absoluteMaxSize,
			int warningDistance,
			int warningDelay) {
		this.action = action;
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
		return state == ConnectionState.PLAY && "WORLD_BORDER".equals(name);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 755;
	}

	public Action action() {
		return action;
	}

	public void setAction(Action action) {
		Objects.requireNonNull(action, "action");
		this.action = action;
	}

	@Override
	public Position center() {
		return action == Action.SET_CENTER || action == Action.INITIALIZE ? center : null;
	}

	@Override
	public Double size() {
		return action == Action.SET_SIZE || action == Action.INITIALIZE ? newSize : null;
	}

	@Override
	public Double oldSize() {
		return action == Action.LERP_SIZE || action == Action.INITIALIZE ? oldSize : null;
	}

	@Override
	public Double newSize() {
		return action == Action.LERP_SIZE || action == Action.INITIALIZE ? newSize : null;
	}

	@Override
	public Long lerpTime() {
		return action == Action.LERP_SIZE || action == Action.INITIALIZE ? lerpTime : null;
	}

	@Override
	public Integer absoluteMaxSize() {
		return action == Action.INITIALIZE ? absoluteMaxSize : null;
	}

	@Override
	public void setCenter(Position value) {
		if (action != Action.INITIALIZE) setAction(Action.SET_CENTER);
		center = Objects.requireNonNull(value, "center");
	}

	@Override
	public void setSize(double value) {
		if (action != Action.INITIALIZE) setAction(Action.SET_SIZE);
		newSize = value;
	}

	@Override
	public void setLerp(double oldSize, double newSize, long milliseconds) {
		if (action != Action.INITIALIZE) setAction(Action.LERP_SIZE);
		this.oldSize = oldSize;
		this.newSize = newSize;
		lerpTime = milliseconds;
	}

	@Override
	public void setAbsoluteMaxSize(int value) {
		setAction(Action.INITIALIZE);
		absoluteMaxSize = value;
	}

	public double rawOldSize() {
		return oldSize;
	}

	public double rawNewSize() {
		return newSize;
	}

	public long rawLerpTime() {
		return lerpTime;
	}

	public int warningDelay() {
		return warningDelay;
	}

	public int warningDistance() {
		return warningDistance;
	}

	public enum Action {
		SET_SIZE,
		LERP_SIZE,
		SET_CENTER,
		INITIALIZE,
		WARNING_DELAY,
		WARNING_DISTANCE
	}
}

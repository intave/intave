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

package de.jpx3.intave.packet.wire.player.teleport;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerTeleportPacket;
import de.jpx3.intave.packet.wire.MutableWirePacket;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.EnumSet;
import java.util.OptionalInt;
import java.util.Set;

public class PlayerTeleportWithoutIdWirePacket extends MutableWirePacket implements MutablePlayerTeleportPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerTeleportWithoutIdWirePacket> STREAM_CODEC =
		StreamCodec.compound(
			Position.STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::position,
			Rotation.STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::rotation,
			Relative.LEGACY_FLAGS_STREAM_CODEC, PlayerTeleportWithoutIdWirePacket::flags,
			PlayerTeleportWithoutIdWirePacket::new
		);

	protected Position position;
	protected Rotation rotation;
	protected Set<Relative> flags;

	public PlayerTeleportWithoutIdWirePacket() {
		this(new Position(), new Rotation(0, 0), EnumSet.noneOf(Relative.class));
	}

	public PlayerTeleportWithoutIdWirePacket(
		Position position, Rotation rotation, Set<Relative> flags) {
		this.position = position;
		this.rotation = rotation;
		this.flags = flags;
	}

	@Override
	public Mode motionMode() {
		return Mode.OVERRIDE;
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}

	@Override
	public Position position() {
		if (position != null && !position.isImmutable()) markMutated();
		return this.position;
	}

	@Override
	public void setPosition(Position value) {
		markMutated();
		this.position = value;
	}

	@Override
	public Rotation rotation() {
		markMutated();
		return this.rotation;
	}

	@Override
	public void setRotation(Rotation value) {
		markMutated();
		this.rotation = value;
	}

	@Override
	public Set<Relative> flags() {
		markMutated();
		return this.flags;
	}

	@Override
	public void setFlags(Set<Relative> value) {
		markMutated();
		this.flags = value;
	}

	@Override
	public OptionalInt teleportId() {
		return OptionalInt.empty();
	}

	@Override
	public void setTeleportId(int value) {
		markMutated();
		throw new IllegalStateException("No teleport ID before 1.9");
	}

	@Override
	public Motion motion() {
		return null;
	}

	@Override
	public void setMotion(Motion value) {
		markMutated();
		throw new IllegalStateException("No motion before 1.21.2");
	}
}

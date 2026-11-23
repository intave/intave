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

package de.jpx3.intave.packet.wire.player;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerTeleportPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.Motion;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Relative;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.*;

public final class PlayerTeleportWirePacket {
	private PlayerTeleportWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol755.class,
			Protocol762.class,
			Protocol768.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutablePlayerTeleportPacket {
		protected Position position;
		protected Rotation rotation;
		protected Set<Relative> flags;

		protected Base() {
			this(new Position(), new Rotation(0, 0), EnumSet.noneOf(Relative.class));
		}

		protected Base(
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
		public abstract OptionalInt teleportId();

		@Override
		public abstract void setTeleportId(int value);

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

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47> STREAM_CODEC =
			StreamCodec.compound(
				Position.STREAM_CODEC, Protocol47::position,
				Rotation.STREAM_CODEC, Protocol47::rotation,
				Relative.LEGACY_FLAGS_STREAM_CODEC, Protocol47::flags,
				Protocol47::new
			);

		public Protocol47() {
			super();
		}

		public Protocol47(
			Position position, Rotation rotation, Set<Relative> flags) {
			super(position, rotation, flags);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
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
	}

	static class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				Position.STREAM_CODEC, Base::position,
				Rotation.STREAM_CODEC, Base::rotation,
				Relative.LEGACY_FLAGS_STREAM_CODEC, Base::flags,
				ByteBufStreamCodecs.VARINT, Protocol107::teleportIdValue,
				Protocol107::new
			);

		protected int teleportId;

		public Protocol107() {
			this(new Position(), new Rotation(0, 0), EnumSet.noneOf(Relative.class), 0);
		}

		public Protocol107(
			Position position, Rotation rotation, Set<Relative> flags, int teleportId) {
			super(position, rotation, flags);
			this.teleportId = teleportId;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 755;
		}

		@Override
		public OptionalInt teleportId() {
			return OptionalInt.of(teleportId);
		}

		@Override
		public void setTeleportId(int value) {
			markMutated();
			teleportId = value;
		}

		public int teleportIdValue() {
			return teleportId;
		}
	}

	static class Protocol755 extends PlayerTeleportWirePacket.Protocol107 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol755> STREAM_CODEC =
			StreamCodec.compound(
				Position.STREAM_CODEC, Base::position,
				Rotation.STREAM_CODEC, Base::rotation,
				Relative.LEGACY_FLAGS_STREAM_CODEC, Base::flags,
				ByteBufStreamCodecs.VARINT, PlayerTeleportWirePacket.Protocol107::teleportIdValue,
				ByteBufStreamCodecs.BOOLEAN, Protocol755::dismount,
				Protocol755::new
			);

		protected boolean dismount;

		public Protocol755() {
			this(new Position(), new Rotation(0, 0), EnumSet.noneOf(Relative.class), 0, false);
		}

		public Protocol755(
			Position position,
			Rotation rotation,
			Set<Relative> flags,
			int teleportId,
			boolean dismount) {
			super(position, rotation, flags, teleportId);
			this.dismount = dismount;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 755 && protocolVersion < 762;
		}

		public boolean dismount() {
			return dismount;
		}
	}

	static class Protocol762 extends PlayerTeleportWirePacket.Protocol107 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol762> STREAM_CODEC =
			StreamCodec.compound(
				Position.STREAM_CODEC, Base::position,
				Rotation.STREAM_CODEC, Base::rotation,
				Relative.LEGACY_FLAGS_STREAM_CODEC, Base::flags,
				ByteBufStreamCodecs.VARINT, PlayerTeleportWirePacket.Protocol107::teleportIdValue,
				Protocol762::new
			);

		public Protocol762() {
			this(new Position(), new Rotation(0, 0), EnumSet.noneOf(Relative.class), 0);
		}

		public Protocol762(
			Position position, Rotation rotation, Set<Relative> flags, int teleportId) {
			super(position, rotation, flags, teleportId);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 762 && protocolVersion < 768;
		}
	}

	static class Protocol768 extends PlayerTeleportWirePacket.Protocol107 {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol768> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, PlayerTeleportWirePacket.Protocol107::teleportIdValue,
				Position.STREAM_CODEC, Base::position,
				Motion.STREAM_CODEC, Protocol768::motion,
				Rotation.STREAM_CODEC, Base::rotation,
				Relative.FLAGS_STREAM_CODEC, Base::flags,
				Protocol768::new
			);

		protected Motion motion;

		public Protocol768() {
			this(
				0,
				new Position(),
				new Motion(0, 0, 0),
				new Rotation(0, 0),
				EnumSet.noneOf(Relative.class));
		}

		public Protocol768(
			int teleportId,
			Position position,
			Motion motion,
			Rotation rotation,
			Set<Relative> flags) {
			super(position, rotation, flags, teleportId);
			this.motion = motion;
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 768;
		}

		@Override
		public OptionalInt teleportId() {
			return OptionalInt.of(teleportId);
		}

		@Override
		public void setTeleportId(int value) {
			markMutated();
			teleportId = value;
		}

		@Override
		public Mode motionMode() {
			return flags().contains(Relative.ROTATE_DELTA) ? Mode.ADDITIVE : Mode.OVERRIDE;
		}

		@Override
		public Motion motion() {
			markMutated();
			return motion;
		}

		@Override
		public void setMotion(Motion value) {
			markMutated();
			motion = value;
		}
	}
}

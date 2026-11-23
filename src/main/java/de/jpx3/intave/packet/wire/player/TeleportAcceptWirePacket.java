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
import de.jpx3.intave.packet.contract.mutable.MutableTeleportAcceptPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.PositionAndRotation;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TeleportAcceptWirePacket {
	private TeleportAcceptWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol107.class,
			Protocol777.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutableTeleportAcceptPacket {
		protected int teleportId;
		protected PositionAndRotation positionAndRotation;

		protected Base() {
			this(0, null);
		}

		protected Base(int teleportId, PositionAndRotation positionAndRotation) {
			this.teleportId = teleportId;
			this.positionAndRotation = positionAndRotation;
		}

		@Override
		public int teleportId() {
			return this.teleportId;
		}

		@Override
		public void setTeleportId(int value) {
			markMutated();
			this.teleportId = value;
		}

		@Override
		public PositionAndRotation positionAndRotation() {
			return positionAndRotation;
		}

		@Override
		public void setPositionAndRotation(PositionAndRotation value) {
			markMutated();
			positionAndRotation = value;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107> STREAM_CODEC =
			StreamCodec.compound(
				ByteBufStreamCodecs.VARINT, Base::teleportId,
				Protocol107::new
			);

		public Protocol107() {
			super();
		}

		public Protocol107(int teleportId) {
			super(teleportId, null);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 777;
		}
	}

	static final class Protocol777 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol777> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.teleportId);
					PositionAndRotation.STREAM_CODEC.encode(
						output,
						Objects.requireNonNull(
							packet.positionAndRotation, "positionAndRotation"));
				},
				input -> {
					int teleportId = ByteBufStreamCodecs.VARINT.decode(input);
					PositionAndRotation positionAndRotation =
						PositionAndRotation.STREAM_CODEC.decode(input);
					return new Protocol777(teleportId, positionAndRotation);
				});

		public Protocol777() {
			super();
		}

		public Protocol777(int teleportId, PositionAndRotation positionAndRotation) {
			super(teleportId, positionAndRotation);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 777;
		}
	}
}

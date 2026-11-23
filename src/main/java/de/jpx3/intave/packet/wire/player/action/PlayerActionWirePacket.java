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

package de.jpx3.intave.packet.wire.player.action;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerActionPacket;
import de.jpx3.intave.packet.wire.AbstractMutable;
import de.jpx3.intave.share.PlayerAction;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.enumValue;

public final class PlayerActionWirePacket {
	private PlayerActionWirePacket() {
	}

	public static List<Class<?>> registeredClasses() {
		return Collections.unmodifiableList(Arrays.<Class<?>>asList(
			Protocol47.class,
			Protocol107.class,
			Protocol771.class
		));
	}

	static abstract class Base extends AbstractMutable implements MutablePlayerActionPacket {
		protected static final PlayerAction[] PRE_1_9 = {
			PlayerAction.PRESS_SHIFT_KEY,
			PlayerAction.RELEASE_SHIFT_KEY,
			PlayerAction.STOP_SLEEPING,
			PlayerAction.START_SPRINTING,
			PlayerAction.STOP_SPRINTING,
			PlayerAction.START_RIDING_JUMP,
			PlayerAction.OPEN_INVENTORY
		};

		protected static final PlayerAction[] MODERN = {
			PlayerAction.STOP_SLEEPING,
			PlayerAction.START_SPRINTING,
			PlayerAction.STOP_SPRINTING,
			PlayerAction.START_RIDING_JUMP,
			PlayerAction.STOP_RIDING_JUMP,
			PlayerAction.OPEN_INVENTORY,
			PlayerAction.START_FALL_FLYING
		};

		protected static final PlayerAction[] LEGACY = {
			PlayerAction.PRESS_SHIFT_KEY,
			PlayerAction.RELEASE_SHIFT_KEY,
			PlayerAction.STOP_SLEEPING,
			PlayerAction.START_SPRINTING,
			PlayerAction.STOP_SPRINTING,
			PlayerAction.START_RIDING_JUMP,
			PlayerAction.STOP_RIDING_JUMP,
			PlayerAction.OPEN_INVENTORY,
			PlayerAction.START_FALL_FLYING
		};

		protected PlayerAction playerAction = PlayerAction.STOP_SLEEPING;
		protected int entityId, data;

		protected Base() {
			this(0, PlayerAction.STOP_SLEEPING, 0);
		}

		protected Base(int entityId, PlayerAction playerAction, int data) {
			this.entityId = entityId;
			this.playerAction = playerAction;
			this.data = data;
		}

		@Override
		public PlayerAction playerAction() {
			return this.playerAction;
		}

		@Override
		public void setPlayerAction(PlayerAction value) {
			markMutated();
			this.playerAction = value;
		}
	}

	static final class Protocol47 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol47>
			STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
					PlayerAction[] actions = PRE_1_9;
					PlayerAction value =
						packet.playerAction.isStartSneak()
							? PlayerAction.PRESS_SHIFT_KEY
							: packet.playerAction.isStopSneak()
							? PlayerAction.RELEASE_SHIFT_KEY
							: packet.playerAction
							== PlayerAction.RIDING_JUMP
							? PlayerAction.START_RIDING_JUMP
							: packet.playerAction;
					int actionId = Arrays.asList(actions).indexOf(value);
					if (actionId < 0)
						throw new IllegalArgumentException(
							"Unsupported player action: " + packet.playerAction);
					ByteBufStreamCodecs.VARINT.encode(output, actionId);
					ByteBufStreamCodecs.VARINT.encode(output, packet.data);
				},
				input -> {
					int entityId = ByteBufStreamCodecs.VARINT.decode(input);
					int actionId = ByteBufStreamCodecs.VARINT.decode(input);
					PlayerAction playerAction = enumValue(PRE_1_9, actionId);
					int data = ByteBufStreamCodecs.VARINT.decode(input);
					return new Protocol47(
						entityId, playerAction, data);
				});

		public Protocol47() {
			super();
		}

		public Protocol47(int entityId, PlayerAction playerAction, int data) {
			super(entityId, playerAction, data);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 47 && protocolVersion < 107;
		}
	}

	static final class Protocol107 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol107>
			STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
					PlayerAction[] actions = LEGACY;
					PlayerAction value =
						packet.playerAction.isStartSneak()
							? PlayerAction.PRESS_SHIFT_KEY
							: packet.playerAction.isStopSneak()
							? PlayerAction.RELEASE_SHIFT_KEY
							: packet.playerAction
							== PlayerAction.RIDING_JUMP
							? PlayerAction.START_RIDING_JUMP
							: packet.playerAction;
					int actionId = Arrays.asList(actions).indexOf(value);
					if (actionId < 0)
						throw new IllegalArgumentException(
							"Unsupported player action: " + packet.playerAction);
					ByteBufStreamCodecs.VARINT.encode(output, actionId);
					ByteBufStreamCodecs.VARINT.encode(output, packet.data);
				},
				input -> {
					int entityId = ByteBufStreamCodecs.VARINT.decode(input);
					int actionId = ByteBufStreamCodecs.VARINT.decode(input);
					PlayerAction playerAction = enumValue(LEGACY, actionId);
					int data = ByteBufStreamCodecs.VARINT.decode(input);
					return new Protocol107(
						entityId, playerAction, data);
				});

		public Protocol107() {
			super();
		}

		public Protocol107(int entityId, PlayerAction playerAction, int data) {
			super(entityId, playerAction, data);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 107 && protocolVersion < 771;
		}
	}

	static final class Protocol771 extends Base {
		public static final StreamCodec<ByteBuf, ByteBuf, Protocol771> STREAM_CODEC =
			StreamCodec.of(
				(output, packet) -> {
					ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
					PlayerAction[] actions = Protocol771.MODERN;
					PlayerAction value =
						packet.playerAction.isStartSneak()
							? PlayerAction.PRESS_SHIFT_KEY
							: packet.playerAction.isStopSneak()
							? PlayerAction.RELEASE_SHIFT_KEY
							: packet.playerAction == PlayerAction.RIDING_JUMP
							? PlayerAction.START_RIDING_JUMP
							: packet.playerAction;
					int actionId = Arrays.asList(actions).indexOf(value);
					if (actionId < 0)
						throw new IllegalArgumentException(
							"Unsupported player action: " + packet.playerAction);
					ByteBufStreamCodecs.VARINT.encode(output, actionId);
					ByteBufStreamCodecs.VARINT.encode(output, packet.data);
				},
				input -> {
					int entityId = ByteBufStreamCodecs.VARINT.decode(input);
					int actionId = ByteBufStreamCodecs.VARINT.decode(input);
					PlayerAction playerAction =
						enumValue(Protocol771.MODERN, actionId);
					int data = ByteBufStreamCodecs.VARINT.decode(input);
					return new Protocol771(entityId, playerAction, data);
				});

		public Protocol771() {
			super();
		}

		public Protocol771(int entityId, PlayerAction playerAction, int data) {
			super(entityId, playerAction, data);
		}

		@Override
		public boolean appliesTo(int protocolVersion) {
			return protocolVersion >= 771;
		}
	}
}

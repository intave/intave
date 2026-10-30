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
import de.jpx3.intave.packet.ConnectionState;
import de.jpx3.intave.packet.contract.mutable.MutablePlayerActionPacket;
import de.jpx3.intave.share.PlayerAction;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;

import static de.jpx3.intave.packet.wire.WireCodecs.enumValue;

public class PlayerActionWirePacket implements MutablePlayerActionPacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerActionWirePacket> STREAM_CODEC =
			StreamCodec.of(
					(output, packet) -> {
						ByteBufStreamCodecs.VARINT.encode(output, packet.entityId);
						PlayerAction[] actions = PlayerActionWirePacket.MODERN;
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
								enumValue(PlayerActionWirePacket.MODERN, actionId);
						int data = ByteBufStreamCodecs.VARINT.decode(input);
						return new PlayerActionWirePacket(entityId, playerAction, data);
					});

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

	public PlayerActionWirePacket() {
		this(0, PlayerAction.STOP_SLEEPING, 0);
	}

	public PlayerActionWirePacket(int entityId, PlayerAction playerAction, int data) {
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
		this.playerAction = value;
	}

	@Override
	public boolean appliesTo(ConnectionState state, String name) {
		return (state == ConnectionState.PLAY) && ("ENTITY_ACTION".equals(name));
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 771;
	}
}

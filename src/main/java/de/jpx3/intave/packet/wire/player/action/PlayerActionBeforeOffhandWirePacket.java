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
import de.jpx3.intave.share.PlayerAction;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;

import static de.jpx3.intave.packet.wire.WireCodecs.enumValue;

public final class PlayerActionBeforeOffhandWirePacket extends PlayerActionWirePacket {
	public static final StreamCodec<ByteBuf, ByteBuf, PlayerActionBeforeOffhandWirePacket>
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
								return new PlayerActionBeforeOffhandWirePacket(
										entityId, playerAction, data);
							});

	public PlayerActionBeforeOffhandWirePacket() {
		super();
	}

	public PlayerActionBeforeOffhandWirePacket(int entityId, PlayerAction playerAction, int data) {
		super(entityId, playerAction, data);
	}

	@Override
	public boolean appliesTo(int protocolVersion) {
		return protocolVersion >= 47 && protocolVersion < 107;
	}
}

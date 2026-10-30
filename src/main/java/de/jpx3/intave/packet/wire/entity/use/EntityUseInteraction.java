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

package de.jpx3.intave.packet.wire.entity.use;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.EntityUseAction;
import de.jpx3.intave.share.PlayerHand;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;

import java.util.Objects;

/** The action and its dependent fields, between the entity ID and any trailing flags. */
final class EntityUseInteraction {
	private static final StreamCodec<ByteBuf, ByteBuf, PlayerHand> HAND_CODEC =
			ByteBufStreamCodecs.enumCodec(PlayerHand.values())
					.beforeAndAfter(
							hand -> hand,
							hand -> {
								if (hand == null)
									throw new IllegalArgumentException(
											"Entity interaction requires a hand");
								return hand;
							});
	public static final StreamCodec<ByteBuf, ByteBuf, EntityUseInteraction> STREAM_CODEC =
			codec(HAND_CODEC);
	public static final StreamCodec<ByteBuf, ByteBuf, EntityUseInteraction>
			WITHOUT_HAND_STREAM_CODEC = codec(StreamCodec.of((PlayerHand) null));

	private final EntityUseAction action;
	private final RawVector3d hitPosition;
	private final PlayerHand hand;

	public EntityUseInteraction(EntityUseAction action, RawVector3d hitPosition, PlayerHand hand) {
		this.action = Objects.requireNonNull(action, "action");
		this.hitPosition = hitPosition;
		this.hand = hand;
	}

	public EntityUseAction action() {
		return action;
	}

	public RawVector3d hitPosition() {
		return hitPosition;
	}

	public PlayerHand hand() {
		return hand;
	}

	private static StreamCodec<ByteBuf, ByteBuf, EntityUseInteraction> codec(
			StreamCodec<ByteBuf, ByteBuf, PlayerHand> handCodec) {
		return StreamCodec.of(
				(output, interaction) -> {
					if (interaction.action == EntityUseAction.INTERACT_AT
							&& interaction.hitPosition == null) {
						throw new IllegalArgumentException("INTERACT_AT requires a hit position");
					}
					EntityUseAction.WIRE_STREAM_CODEC.encode(output, interaction.action);
					if (interaction.action == EntityUseAction.INTERACT_AT) {
						RawVector3d.FLOAT_STREAM_CODEC.encode(output, interaction.hitPosition);
					}
					if (interaction.action != EntityUseAction.ATTACK) {
						handCodec.encode(output, interaction.hand);
					}
				},
				input -> {
					EntityUseAction action = EntityUseAction.WIRE_STREAM_CODEC.decode(input);
					RawVector3d hitPosition =
							action == EntityUseAction.INTERACT_AT
									? RawVector3d.FLOAT_STREAM_CODEC.decode(input)
									: null;
					PlayerHand hand =
							action != EntityUseAction.ATTACK ? handCodec.decode(input) : null;
					return new EntityUseInteraction(action, hitPosition, hand);
				});
	}
}

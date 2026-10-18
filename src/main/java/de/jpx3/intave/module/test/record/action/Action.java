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

package de.jpx3.intave.module.test.record.action;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.TickRange;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class Action {
	private static final int VERSIONED_LIST_MARKER = Integer.MIN_VALUE;
	private static final int CURRENT_FORMAT_VERSION = 2;
	private static final int MAX_ACTION_COUNT = 1_048_576;
	private static final StreamCodec<ByteBuf, ByteBuf, Action> CURRENT_CODEC = StreamCodec.dispatchBuilder(Action.class, ActionType.STREAM_CODEC)
		.subtype(ActionType.RECEIVE_VELOCITY, ReceiveVelocity.class, () -> ReceiveVelocity.STREAM_CODEC)
		.subtype(ActionType.PISTON_SLIME, PistonSlimeAction.class, () -> PistonSlimeAction.STREAM_CODEC)
		.subtype(ActionType.SHULKER_BOX, ShulkerBoxAction.class, () -> ShulkerBoxAction.STREAM_CODEC)
		.subtype(ActionType.ATTACK_REDUCTION_V2, AttackReduction.class, () -> AttackReduction.STREAM_CODEC)
		.subtype(ActionType.ITEM_USE_PACKET, ItemUsePacketAction.class, () -> ItemUsePacketAction.STREAM_CODEC)
		.subtype(ActionType.ACTIVE_ITEM_REPLACEMENT, ActiveItemReplacementAction.class, () -> ActiveItemReplacementAction.STREAM_CODEC)
		.build();
	private static final StreamCodec<ByteBuf, ByteBuf, Action> LEGACY_CODEC = StreamCodec.dispatchBuilder(Action.class, ActionType.STREAM_CODEC)
		.subtype(ActionType.RECEIVE_VELOCITY, ReceiveVelocity.class, () -> ReceiveVelocity.STREAM_CODEC)
		.subtype(ActionType.PISTON_SLIME, PistonSlimeAction.class, () -> PistonSlimeAction.LEGACY_STREAM_CODEC)
		.subtype(ActionType.SHULKER_BOX, ShulkerBoxAction.class, () -> ShulkerBoxAction.LEGACY_STREAM_CODEC)
		.subtype(ActionType.ATTACK_REDUCTION_V2, AttackReduction.class, () -> AttackReduction.STREAM_CODEC)
		.subtype(ActionType.ITEM_USE_PACKET, ItemUsePacketAction.class, () -> ItemUsePacketAction.STREAM_CODEC)
		.subtype(ActionType.ACTIVE_ITEM_REPLACEMENT, ActiveItemReplacementAction.class, () -> ActiveItemReplacementAction.STREAM_CODEC)
		.build();
	private static final StreamCodec<ByteBuf, ByteBuf, Action> LEGACY_STREAM_CODEC =
		withLegacyAttackReduction(LEGACY_CODEC);
	public static final StreamCodec<ByteBuf, ByteBuf, Action> STREAM_CODEC =
		withLegacyAttackReduction(CURRENT_CODEC);
	public static final StreamCodec<ByteBuf, ByteBuf, List<Action>> LIST_STREAM_CODEC = StreamCodec.of(
		(buffer, actions) -> {
			ByteBufStreamCodecs.INTEGER.encode(buffer, VERSIONED_LIST_MARKER);
			ByteBufStreamCodecs.INTEGER.encode(buffer, CURRENT_FORMAT_VERSION);
			ByteBufStreamCodecs.INTEGER.encode(buffer, actions.size());
			for (Action action : actions) {
				STREAM_CODEC.encode(buffer, action);
			}
		},
		buffer -> {
			int markerOrLegacySize = ByteBufStreamCodecs.INTEGER.decode(buffer);
			if (markerOrLegacySize >= 0) {
				return decodeActions(buffer, markerOrLegacySize, LEGACY_STREAM_CODEC);
			}
			if (markerOrLegacySize != VERSIONED_LIST_MARKER) {
				throw new IllegalStateException("Unknown action list marker: " + markerOrLegacySize);
			}
			int version = ByteBufStreamCodecs.INTEGER.decode(buffer);
			if (version != CURRENT_FORMAT_VERSION) {
				throw new IllegalStateException("Unsupported action list format version: " + version);
			}
			return decodeActions(
				buffer, ByteBufStreamCodecs.INTEGER.decode(buffer), STREAM_CODEC
			);
		}
	);

	private static StreamCodec<ByteBuf, ByteBuf, Action> withLegacyAttackReduction(
		StreamCodec<ByteBuf, ByteBuf, Action> codec
	) {
		return StreamCodec.of(codec::encode, buffer -> {
			int start = buffer.readerIndex();
			if (ActionType.STREAM_CODEC.decode(buffer) == ActionType.ATTACK_REDUCTION) {
				// Legacy payloads contain only the range and may have multiple attacks per tick.
				return new AttackReduction(false, true, TickRange.STREAM_CODEC.decode(buffer));
			}
			buffer.readerIndex(start);
			return codec.decode(buffer);
		});
	}

	private static List<Action> decodeActions(
		ByteBuf buffer, int size, StreamCodec<ByteBuf, ByteBuf, Action> codec
	) {
		if (size < 0 || size > MAX_ACTION_COUNT) {
			throw new IllegalStateException("Invalid action count: " + size);
		}
		List<Action> actions = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			actions.add(codec.decode(buffer));
		}
		return actions;
	}

	public abstract @NotNull ActionType type();
}

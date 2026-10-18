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
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class ShulkerBoxAction extends Action {
	private static final StreamCodec<ByteBuf, ByteBuf, Direction> DIRECTION_CODEC =
		ByteBufStreamCodecs.INTEGER.beforeAndAfter(Direction::getFront, Direction::getIndex);
	static final StreamCodec<ByteBuf, ByteBuf, ShulkerBoxAction> LEGACY_STREAM_CODEC =
		streamCodec(BlockPosition.LEGACY_STREAM_CODEC);
	public static final StreamCodec<ByteBuf, ByteBuf, ShulkerBoxAction> STREAM_CODEC =
		streamCodec(BlockPosition.STREAM_CODEC);

	private final BlockPosition position;
	private final Direction direction;
	private final boolean opening;
	private final TickRange tickRange;

	private static StreamCodec<ByteBuf, ByteBuf, ShulkerBoxAction> streamCodec(
		StreamCodec<ByteBuf, ByteBuf, BlockPosition> positionCodec
	) {
		return StreamCodec.compound(
			positionCodec, ShulkerBoxAction::position,
			DIRECTION_CODEC, ShulkerBoxAction::direction,
			ByteBufStreamCodecs.BOOLEAN, ShulkerBoxAction::opening,
			TickRange.STREAM_CODEC, ShulkerBoxAction::tickRange,
			ShulkerBoxAction::new
		);
	}

	public ShulkerBoxAction(
		BlockPosition position,
		Direction direction,
		boolean opening,
		TickRange tickRange
	) {
		this.position = Objects.requireNonNull(position, "position");
		this.direction = Objects.requireNonNull(direction, "direction");
		this.opening = opening;
		this.tickRange = Objects.requireNonNull(tickRange, "tickRange");
	}

	public BlockPosition position() {
		return position;
	}

	public Direction direction() {
		return direction;
	}

	public boolean opening() {
		return opening;
	}

	public TickRange tickRange() {
		return tickRange;
	}

	@Override
	public @NotNull ActionType type() {
		return ActionType.SHULKER_BOX;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof ShulkerBoxAction)) {
			return false;
		}
		ShulkerBoxAction other = (ShulkerBoxAction) object;
		return opening == other.opening
			&& position.equals(other.position)
			&& direction == other.direction
			&& tickRange.equals(other.tickRange);
	}

	@Override
	public int hashCode() {
		return Objects.hash(position, direction, opening, tickRange);
	}

	@Override
	public String toString() {
		return "ShulkerBoxAction{position=" + position
			+ ", direction=" + direction
			+ ", opening=" + opening
			+ ", tickRange=" + tickRange + '}';
	}
}

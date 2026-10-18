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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class PistonSlimeAction extends Action {
	private static final StreamCodec<ByteBuf, ByteBuf, Direction> DIRECTION_CODEC =
		ByteBufStreamCodecs.INTEGER.beforeAndAfter(Direction::getFront, Direction::getIndex);
	static final StreamCodec<ByteBuf, ByteBuf, PistonSlimeAction> LEGACY_STREAM_CODEC =
		streamCodec(BlockPosition.LEGACY_STREAM_CODEC);
	public static final StreamCodec<ByteBuf, ByteBuf, PistonSlimeAction> STREAM_CODEC =
		streamCodec(BlockPosition.STREAM_CODEC);

	private final Direction direction;
	private final List<BlockPosition> slimeSources;
	private final TickRange tickRange;

	private static StreamCodec<ByteBuf, ByteBuf, PistonSlimeAction> streamCodec(
		StreamCodec<ByteBuf, ByteBuf, BlockPosition> positionCodec
	) {
		return StreamCodec.compound(
			DIRECTION_CODEC, PistonSlimeAction::direction,
			ByteBufStreamCodecs.listCodecOf(positionCodec), PistonSlimeAction::slimeSources,
			TickRange.STREAM_CODEC, PistonSlimeAction::tickRange,
			PistonSlimeAction::new
		);
	}

	public PistonSlimeAction(
		Direction direction,
		List<BlockPosition> slimeSources,
		TickRange tickRange
	) {
		this.direction = Objects.requireNonNull(direction, "direction");
		this.slimeSources = Collections.unmodifiableList(new ArrayList<>(slimeSources));
		this.tickRange = Objects.requireNonNull(tickRange, "tickRange");
	}

	public Direction direction() {
		return direction;
	}

	public List<BlockPosition> slimeSources() {
		return slimeSources;
	}

	public TickRange tickRange() {
		return tickRange;
	}

	@Override
	public @NotNull ActionType type() {
		return ActionType.PISTON_SLIME;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof PistonSlimeAction)) {
			return false;
		}
		PistonSlimeAction other = (PistonSlimeAction) object;
		return direction == other.direction
			&& slimeSources.equals(other.slimeSources)
			&& tickRange.equals(other.tickRange);
	}

	@Override
	public int hashCode() {
		return Objects.hash(direction, slimeSources, tickRange);
	}

	@Override
	public String toString() {
		return "PistonSlimeAction{direction=" + direction
			+ ", slimeSources=" + slimeSources
			+ ", tickRange=" + tickRange + '}';
	}
}

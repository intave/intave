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

package de.jpx3.intave.packet.wire.entity.position;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.Position;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static de.jpx3.intave.packet.wire.WireCodecs.count;

final class EntityPositionPath {
	public static final StreamCodec<ByteBuf, ByteBuf, EntityPositionPath> STREAM_CODEC = StreamCodec.of(
		(output, path) -> {
			ByteBufStreamCodecs.VARINT.encode(output, path.stepped ? 1 : 0);
			if (path.stepped) {
				ByteBufStreamCodecs.VARINT.encode(output, path.steps.size());
				for (Step step : path.steps) Step.STREAM_CODEC.encode(output, step);
			} else {
				Position.STREAM_CODEC.encode(output, path.endPosition());
			}
		},
		input -> {
			int type = ByteBufStreamCodecs.VARINT.decode(input);
			if (type == 0) return linear(Position.STREAM_CODEC.decode(input));
			if (type != 1) throw new IllegalArgumentException("Invalid position path type: " + type);
			int size = count(input, input.readableBytes() / 25);
			List<Step> steps = new ArrayList<>(size);
			for (int i = 0; i < size; i++) steps.add(Step.STREAM_CODEC.decode(input));
			return new EntityPositionPath(true, steps);
		}
	);

	private final boolean stepped;
	private final List<Step> steps;

	public EntityPositionPath(boolean stepped, List<Step> steps) {
		if (steps.isEmpty()) throw new IllegalArgumentException("Empty position path");
		if (!stepped && (steps.size() != 1 || steps.get(0).tickOffset() != 0)) {
			throw new IllegalArgumentException("Linear position paths require one step with a zero tick offset");
		}
		this.stepped = stepped;
		this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
	}

	public static EntityPositionPath linear(Position position) {
		return new EntityPositionPath(false, Collections.singletonList(new Step(position, 0)));
	}

	public boolean stepped() {
		return stepped;
	}

	public List<Step> steps() {
		return steps;
	}

	public Position endPosition() {
		return steps.get(steps.size() - 1).position();
	}

	public EntityPositionPath withEndPosition(Position position) {
		List<Step> updated = new ArrayList<>(steps);
		int last = updated.size() - 1;
		updated.set(last, new Step(position, updated.get(last).tickOffset()));
		return new EntityPositionPath(stepped, updated);
	}

	public static final class Step {
		public static final StreamCodec<ByteBuf, ByteBuf, Step> STREAM_CODEC = StreamCodec.compound(
			Position.STREAM_CODEC, Step::position,
			ByteBufStreamCodecs.VARINT, Step::tickOffset,
			Step::new
		);

		private final Position position;
		private final int tickOffset;

		public Step(Position position, int tickOffset) {
			this.position = Objects.requireNonNull(position, "position");
			this.tickOffset = tickOffset;
		}

		public Position position() {
			return position;
		}

		public int tickOffset() {
			return tickOffset;
		}
	}
}

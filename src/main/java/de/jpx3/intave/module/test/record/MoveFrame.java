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

package de.jpx3.intave.module.test.record;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.check.movement.physics.environment.Pose;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Input;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.*;

import static de.jpx3.intave.codec.ByteBufStreamCodecs.*;

public final class MoveFrame {
	private static final int VERSIONED_LIST_MARKER = Integer.MIN_VALUE;
	private static final int CURRENT_FORMAT_VERSION = 5;
	private static final int MAX_FRAME_COUNT = 1_048_576;
	private static final StreamCodec<ByteBuf, ByteBuf, Position> POSITION_CODEC = Position.STREAM_CODEC.nullable(BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Rotation> ROTATION_CODEC = Rotation.STREAM_CODEC.nullable(BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Pose> POSE_CODEC = STRING.beforeAndAfter(Pose::valueOf, Pose::name).nullable(BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, MovementFrameState> MOVEMENT_STATE_CODEC = MovementFrameState.STREAM_CODEC.nullable(BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Boolean> CLIENT_GROUND_CODEC = BOOLEAN.nullable(BOOLEAN);
	private static final StreamCodec<ByteBuf, ByteBuf, Map<BlockPosition, MaterialVariantStore>> LEGACY_BLOCKS_CODEC = StreamCodec.mapCodec(BlockPosition.LEGACY_STREAM_CODEC, MaterialVariantStore.STREAM_CODEC, INTEGER);
	private static final StreamCodec<ByteBuf, ByteBuf, Map<BlockPosition, MaterialVariantStore>> BLOCKS_CODEC = StreamCodec.mapCodec(BlockPosition.STREAM_CODEC, MaterialVariantStore.STREAM_CODEC, INTEGER);

	private final Map<BlockPosition, MaterialVariantStore> dirtyBlocks = new HashMap<>();
	private final Position moveTo;
	private final Rotation rotateTo;
	private final Input input;
	private final boolean gliding;
	private final @Nullable Pose physicalPose;
	private final @Nullable MovementFrameState movementState;
	private final @Nullable Boolean clientOnGround;

	private static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> LEGACY_BASE_STREAM_CODEC =
		baseStreamCodec(LEGACY_BLOCKS_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> VERSION_2_STREAM_CODEC =
		version2StreamCodec(LEGACY_BASE_STREAM_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> VERSION_3_STREAM_CODEC =
		version3StreamCodec(VERSION_2_STREAM_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> VERSION_4_STREAM_CODEC =
		version4StreamCodec(VERSION_3_STREAM_CODEC);
	private static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> CURRENT_STREAM_CODEC = version4StreamCodec(
		version3StreamCodec(version2StreamCodec(baseStreamCodec(BLOCKS_CODEC)))
	);

	public static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> LIST_STREAM_CODEC = StreamCodec.of((buffer, frames) -> {
		INTEGER.encode(buffer, VERSIONED_LIST_MARKER);
		INTEGER.encode(buffer, CURRENT_FORMAT_VERSION);
		INTEGER.encode(buffer, frames.size());
		for (MoveFrame frame : frames) {
			CURRENT_STREAM_CODEC.encode(buffer, frame);
		}
	}, buffer -> {
		int markerOrLegacySize = INTEGER.decode(buffer);
		if (markerOrLegacySize >= 0) {
			return decodeFrames(buffer, markerOrLegacySize, LEGACY_BASE_STREAM_CODEC);
		}
		if (markerOrLegacySize != VERSIONED_LIST_MARKER) {
			throw new IllegalStateException("Unknown movement frame list marker: " + markerOrLegacySize);
		}
		int version = INTEGER.decode(buffer);
		int size = INTEGER.decode(buffer);
		if (version == 2) {
			return decodeFrames(buffer, size, VERSION_2_STREAM_CODEC);
		}
		if (version == 3) {
			return decodeFrames(buffer, size, VERSION_3_STREAM_CODEC);
		}
		if (version == 4) {
			return decodeFrames(buffer, size, VERSION_4_STREAM_CODEC);
		}
		if (version == CURRENT_FORMAT_VERSION) {
			return decodeFrames(buffer, size, CURRENT_STREAM_CODEC);
		}
		throw new IllegalStateException("Unsupported movement frame format version: " + version);
	});

	public MoveFrame(@Nullable Position moveTo, @Nullable Rotation rotateTo, Map<BlockPosition, MaterialVariantStore> dirtyBlocks, Input input, boolean gliding, @Nullable Pose physicalPose) {
		this(moveTo, rotateTo, dirtyBlocks, input, gliding, physicalPose, null);
	}

	public MoveFrame(@Nullable Position moveTo, @Nullable Rotation rotateTo, Map<BlockPosition, MaterialVariantStore> dirtyBlocks, Input input, boolean gliding, @Nullable Pose physicalPose, @Nullable MovementFrameState movementState) {
		this(moveTo, rotateTo, dirtyBlocks, input, gliding, physicalPose, movementState, null);
	}

	public MoveFrame(@Nullable Position moveTo, @Nullable Rotation rotateTo, Map<BlockPosition, MaterialVariantStore> dirtyBlocks, Input input, boolean gliding, @Nullable Pose physicalPose, @Nullable MovementFrameState movementState, @Nullable Boolean clientOnGround) {
		this.moveTo = moveTo;
		this.rotateTo = rotateTo;
		this.dirtyBlocks.putAll(dirtyBlocks);
		this.input = input;
		this.gliding = gliding;
		this.physicalPose = physicalPose;
		this.movementState = movementState;
		this.clientOnGround = clientOnGround;
	}

	public Map<BlockPosition, MaterialVariantStore> blocks() {
		return dirtyBlocks;
	}

	public @Nullable Position moveTo() {
		return moveTo;
	}

	public @Nullable Rotation rotateTo() {
		return rotateTo;
	}

	public Input input() {
		return input;
	}

	public boolean gliding() {
		return gliding;
	}

	public @Nullable Pose physicalPose() {
		return physicalPose;
	}

	public @Nullable MovementFrameState movementState() {
		return movementState;
	}

	private static StreamCodec<ByteBuf, ByteBuf, MoveFrame> baseStreamCodec(
		StreamCodec<ByteBuf, ByteBuf, Map<BlockPosition, MaterialVariantStore>> blocksCodec
	) {
		return StreamCodec.compound(
			POSITION_CODEC, MoveFrame::moveTo,
			ROTATION_CODEC, MoveFrame::rotateTo,
			blocksCodec, MoveFrame::blocks,
			Input.STREAM_CODEC, MoveFrame::input,
			(moveTo, rotateTo, blocks, input) -> new MoveFrame(
				moveTo, rotateTo, blocks, input, false, null
			)
		);
	}

	private static StreamCodec<ByteBuf, ByteBuf, MoveFrame> version2StreamCodec(
		StreamCodec<ByteBuf, ByteBuf, MoveFrame> baseCodec
	) {
		return StreamCodec.of((buffer, frame) -> {
			baseCodec.encode(buffer, frame);
			BOOLEAN.encode(buffer, frame.gliding());
			POSE_CODEC.encode(buffer, frame.physicalPose());
		}, buffer -> {
			MoveFrame frame = baseCodec.decode(buffer);
			return new MoveFrame(
				frame.moveTo, frame.rotateTo, frame.dirtyBlocks, frame.input,
				BOOLEAN.decode(buffer), POSE_CODEC.decode(buffer)
			);
		});
	}

	private static StreamCodec<ByteBuf, ByteBuf, MoveFrame> version3StreamCodec(
		StreamCodec<ByteBuf, ByteBuf, MoveFrame> version2Codec
	) {
		return StreamCodec.of((buffer, frame) -> {
			version2Codec.encode(buffer, frame);
			MOVEMENT_STATE_CODEC.encode(buffer, frame.movementState());
		}, buffer -> {
			MoveFrame frame = version2Codec.decode(buffer);
			return new MoveFrame(
				frame.moveTo, frame.rotateTo, frame.dirtyBlocks, frame.input,
				frame.gliding, frame.physicalPose, MOVEMENT_STATE_CODEC.decode(buffer)
			);
		});
	}

	private static StreamCodec<ByteBuf, ByteBuf, MoveFrame> version4StreamCodec(
		StreamCodec<ByteBuf, ByteBuf, MoveFrame> version3Codec
	) {
		return StreamCodec.of((buffer, frame) -> {
			version3Codec.encode(buffer, frame);
			CLIENT_GROUND_CODEC.encode(buffer, frame.clientOnGround());
		}, buffer -> {
			MoveFrame frame = version3Codec.decode(buffer);
			return new MoveFrame(
				frame.moveTo, frame.rotateTo, frame.dirtyBlocks, frame.input,
				frame.gliding, frame.physicalPose, frame.movementState,
				CLIENT_GROUND_CODEC.decode(buffer)
			);
		});
	}

	public @Nullable Boolean clientOnGround() {
		return clientOnGround;
	}

	@Override
	public String toString() {
		return "MoveFrame{" + "moveTo=" + moveTo + ", rotateTo=" + rotateTo + ", dirtyBlocks=" + dirtyBlocks + ", input=" + input + ", gliding=" + gliding + ", physicalPose=" + physicalPose + ", clientOnGround=" + clientOnGround + '}';
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		MoveFrame moveFrame = (MoveFrame) obj;
		if (!dirtyBlocks.equals(moveFrame.dirtyBlocks)) return false;
		if (!Objects.equals(moveTo, moveFrame.moveTo)) return false;
		if (!Objects.equals(rotateTo, moveFrame.rotateTo)) return false;
		if (!Objects.equals(input, moveFrame.input)) return false;
		if (gliding != moveFrame.gliding) return false;
		if (!Objects.equals(physicalPose, moveFrame.physicalPose)) return false;
		if (!Objects.equals(movementState, moveFrame.movementState)) return false;
		return Objects.equals(clientOnGround, moveFrame.clientOnGround);
	}

	@Override
	public int hashCode() {
		int result = dirtyBlocks.hashCode();
		result = 31 * result + (moveTo != null ? moveTo.hashCode() : 0);
		result = 31 * result + (rotateTo != null ? rotateTo.hashCode() : 0);
		result = 31 * result + input.hashCode();
		result = 31 * result + Boolean.hashCode(gliding);
		result = 31 * result + (physicalPose != null ? physicalPose.hashCode() : 0);
		result = 31 * result + (movementState != null ? movementState.hashCode() : 0);
		result = 31 * result + (clientOnGround != null ? clientOnGround.hashCode() : 0);
		return result;
	}

	private static List<MoveFrame> decodeFrames(ByteBuf buffer, int size, StreamCodec<ByteBuf, ByteBuf, MoveFrame> frameCodec) {
		if (size < 0 || size > MAX_FRAME_COUNT) {
			throw new IllegalStateException("Invalid movement frame count: " + size);
		}
		List<MoveFrame> frames = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			frames.add(frameCodec.decode(buffer));
		}
		return frames;
	}
}

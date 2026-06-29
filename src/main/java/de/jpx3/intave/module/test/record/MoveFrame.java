package de.jpx3.intave.module.test.record;

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Input;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.Rotation;
import io.netty.buffer.ByteBuf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class MoveFrame {
	private final Map<BlockPosition, MaterialVariantStore> dirtyBlocks = new HashMap<>();
	private final Position moveTo;
	private final Rotation rotateTo;
	private final Input input;

	public static final StreamCodec<ByteBuf, ByteBuf, MoveFrame> STREAM_CODEC = StreamCodec.compound(
		Position.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN), MoveFrame::moveTo,
		Rotation.STREAM_CODEC.nullable(ByteBufStreamCodecs.BOOLEAN), MoveFrame::rotateTo,
		StreamCodec.mapCodec(BlockPosition.STREAM_CODEC, MaterialVariantStore.STREAM_CODEC, ByteBufStreamCodecs.INTEGER), MoveFrame::blocks,
		Input.STREAM_CODEC, MoveFrame::input,
		MoveFrame::new
	);

	public static final StreamCodec<ByteBuf, ByteBuf, List<MoveFrame>> LIST_STREAM_CODEC =
		ByteBufStreamCodecs.listCodecOf(MoveFrame.STREAM_CODEC);


	public MoveFrame(
		@Nullable Position moveTo, @Nullable Rotation rotateTo,
		Map<BlockPosition, MaterialVariantStore> dirtyBlocks,
		Input input
	) {
		this.moveTo = moveTo;
		this.rotateTo = rotateTo;
		this.dirtyBlocks.putAll(dirtyBlocks);
		this.input = input;
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

	@Override
	public String toString() {
		return "MoveFrame{" +
			"moveTo=" + moveTo +
			", rotateTo=" + rotateTo +
			", dirtyBlocks=" + dirtyBlocks +
			'}';
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		MoveFrame moveFrame = (MoveFrame) obj;
		if (!dirtyBlocks.equals(moveFrame.dirtyBlocks)) return false;
		if (!Objects.equals(moveTo, moveFrame.moveTo)) return false;
		return Objects.equals(rotateTo, moveFrame.rotateTo);
	}

	@Override
	public int hashCode() {
		int result = dirtyBlocks.hashCode();
		result = 31 * result + (moveTo != null ? moveTo.hashCode() : 0);
		result = 31 * result + (rotateTo != null ? rotateTo.hashCode() : 0);
		return result;
	}
}
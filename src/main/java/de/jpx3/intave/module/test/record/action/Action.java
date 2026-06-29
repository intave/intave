package de.jpx3.intave.module.test.record.action;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class Action {
	public static final StreamCodec<ByteBuf, ByteBuf, Action> STREAM_CODEC = StreamCodec.dispatchBuilder(Action.class, ActionType.STREAM_CODEC)
		.subtype(ActionType.RECEIVE_VELOCITY, ReceiveVelocity.class, () -> ReceiveVelocity.STREAM_CODEC)
		.build();
	public static final StreamCodec<ByteBuf, ByteBuf, List<Action>> LIST_STREAM_CODEC =
		ByteBufStreamCodecs.listCodecOf(STREAM_CODEC);

	public abstract @NotNull ActionType type();
}

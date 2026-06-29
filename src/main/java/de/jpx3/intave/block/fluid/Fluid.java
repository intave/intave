package de.jpx3.intave.block.fluid;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

public interface Fluid {
	StreamCodec<ByteBuf, ByteBuf, Fluid> STREAM_CODEC = StreamCodec.dispatchBuilder(Fluid.class, ByteBufStreamCodecs.UNSIGNED_BYTE)
		.subtype(1, Dry.class, () -> Dry.STREAM_CODEC)
		.subtype(2, Water.class, () -> Water.STREAM_CODEC)
		.subtype(3, Lava.class, () -> Lava.STREAM_CODEC)
		.build();

	boolean isDry();

	boolean isOfWater();

	boolean isOfLava();

	float height();

	int level();

	boolean falling();

	boolean isSource();

	default boolean affectsFlow(Fluid other) {
		return other.isOfWater() || other.similarTo(this);
	}

	default boolean similarTo(Fluid other) {
		return isOfWater() == other.isOfWater() && isOfLava() == other.isOfLava();
	}
}

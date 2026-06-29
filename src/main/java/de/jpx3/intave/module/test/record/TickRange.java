package de.jpx3.intave.module.test.record;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.codec.StreamCodec;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.ThreadLocalRandom;

public final class TickRange {
	private final long startInclusive;
	private final long endExclusive;

	public static final StreamCodec<ByteBuf, ByteBuf, TickRange> STREAM_CODEC = StreamCodec.compound(
		ByteBufStreamCodecs.LONG, TickRange::start,
		ByteBufStreamCodecs.LONG, TickRange::end,
		TickRange::new
	);

	public TickRange(
		long start, long end
	) {
		this.startInclusive = start;
		this.endExclusive = end;
	}

	public boolean couldHaveHappenedIn(
		long tick
	) {
		return tick >= startInclusive && tick < endExclusive;
	}

	public long start() {
		return startInclusive;
	}

	public long end() {
		return endExclusive;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		TickRange other = (TickRange) obj;
		return startInclusive == other.startInclusive && endExclusive == other.endExclusive;
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(startInclusive);
		result = 31 * result + Long.hashCode(endExclusive);
		return result;
	}

	@Override
	public String toString() {
		return "TickRange{" +
			"startInclusive=" + startInclusive +
			", endExclusive=" + endExclusive +
			'}';
	}

	public static TickRange betweenExclusive(long startInclusive, long endExclusive) {
		return new TickRange(startInclusive, endExclusive);
	}

	public static TickRange betweenInclusive(long startInclusive, long endInclusive) {
		return new TickRange(startInclusive, endInclusive + 1);
	}

	public static TickRange random() {
		ThreadLocalRandom current = ThreadLocalRandom.current();
		long start = current.nextInt(256);
		return betweenExclusive(start, start + current.nextInt(256));
	}
}

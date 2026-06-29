package de.jpx3.intave.module.test.record.action;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public final class ReceiveVelocity extends Action {
	private final Motion motion;
	private final TickRange tickRange;

	public static final StreamCodec<ByteBuf, ByteBuf, ReceiveVelocity> STREAM_CODEC = StreamCodec.compound(
		Motion.STREAM_CODEC,
		ReceiveVelocity::motion,
		TickRange.STREAM_CODEC,
		ReceiveVelocity::tickRange,
		ReceiveVelocity::new
	);

	public ReceiveVelocity(
		Motion motion,
		TickRange range
	) {
		this.motion = motion;
		this.tickRange = range;
	}

	public Motion motion() {
		return motion;
	}

	public TickRange tickRange() {
		return tickRange;
	}

	@Override
	public @NotNull ActionType type() {
		return ActionType.RECEIVE_VELOCITY;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		ReceiveVelocity that = (ReceiveVelocity) obj;
		return motion.equals(that.motion) && tickRange.equals(that.tickRange);
	}

	@Override
	public int hashCode() {
		int result = motion.hashCode();
		result = 31 * result + tickRange.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "ReceiveVelocity{" +
			"motion=" + motion +
			", tickRange=" + tickRange +
			'}';
	}
}

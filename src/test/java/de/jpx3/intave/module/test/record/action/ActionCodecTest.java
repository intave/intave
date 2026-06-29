package de.jpx3.intave.module.test.record.action;

import de.jpx3.intave.codec.StreamCodec;
import de.jpx3.intave.module.test.record.TickRange;
import de.jpx3.intave.share.Motion;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ActionCodecTest {

	@Test
	public void testReceiveVelocity() {
		ReceiveVelocity receiveVelocity = new ReceiveVelocity(Motion.random(), TickRange.random());

		ByteBuf buf = Unpooled.buffer();
		StreamCodec<ByteBuf, ByteBuf, Action> actionCodec = Action.STREAM_CODEC;

		actionCodec.encode(buf, receiveVelocity);
		Action reconstructed = actionCodec.decode(buf);

		assertInstanceOf(ReceiveVelocity.class, reconstructed);
		assertEquals(receiveVelocity, reconstructed);
	}
}
package de.jpx3.intave.share;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InputTest {

	@Test
	void streamCodecRoundTripsEveryFlagCombination() {
		for (int flags = 0; flags < 128; flags++) {
			Input expected = inputFromFlags(flags);
			ByteBuf buffer = Unpooled.buffer();
			try {
				Input.STREAM_CODEC.encode(buffer, expected);

				assertEquals(1, buffer.readableBytes());
				assertEquals(flags, buffer.getUnsignedByte(buffer.readerIndex()));
				assertEquals(expected, Input.STREAM_CODEC.decode(buffer));
				assertEquals(0, buffer.readableBytes());
			} finally {
				buffer.release();
			}
		}
	}

	@Test
	void streamCodecIgnoresUnusedHighBitWhenDecoding() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			buffer.writeByte(0xff);

			Input decoded = Input.STREAM_CODEC.decode(buffer);

			assertEquals(inputFromFlags(0x7f), decoded);
			assertEquals(0, buffer.readableBytes());
		} finally {
			buffer.release();
		}
	}

	private static Input inputFromFlags(
		int flags
	) {
		return new Input(
			(flags & 1) != 0,
			(flags & 2) != 0,
			(flags & 4) != 0,
			(flags & 8) != 0,
			(flags & 16) != 0,
			(flags & 32) != 0,
			(flags & 64) != 0
		);
	}
}

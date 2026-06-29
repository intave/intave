package de.jpx3.intave.block.fluid;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FluidCodecTest {
	@Test
	void dryRoundTrips() {
		Fluid decoded = roundTrip(Dry.of());

		assertSame(Dry.of(), decoded);
		assertTrue(decoded.isDry());
	}

	@Test
	void waterRoundTrips() {
		Fluid decoded = roundTrip(Water.of(0.75F, 3, false));

		assertTrue(decoded.isOfWater());
		assertFalse(decoded.isOfLava());
		assertEquals(0.75F, decoded.height(), 0.0F);
		assertEquals(3, decoded.level());
		assertFalse(decoded.falling());
	}

	@Test
	void lavaRoundTrips() {
		Fluid decoded = roundTrip(Lava.of(0.5F, 8, true));

		assertTrue(decoded.isOfLava());
		assertFalse(decoded.isOfWater());
		assertEquals(0.5F, decoded.height(), 0.0F);
		assertEquals(8, decoded.level());
		assertTrue(decoded.falling());
	}

	@Test
	void unknownFluidTypeFails() {
		ByteBuf buffer = Unpooled.buffer();
		try {
			buffer.writeByte(255);

			assertThrows(IllegalStateException.class, () -> Fluid.STREAM_CODEC.decode(buffer));
		} finally {
			buffer.release();
		}
	}

	private static Fluid roundTrip(
		Fluid fluid
	) {
		ByteBuf buffer = Unpooled.buffer();
		try {
			Fluid.STREAM_CODEC.encode(buffer, fluid);
			return Fluid.STREAM_CODEC.decode(buffer);
		} finally {
			buffer.release();
		}
	}
}

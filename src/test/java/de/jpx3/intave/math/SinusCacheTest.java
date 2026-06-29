package de.jpx3.intave.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SinusCacheTest {

	@Test
	void testSinusCache() {
		for (boolean fastMath : new boolean[]{false, true}) {
			for (int i = 0; i < 360; i++) {
				float angleInRadians = (i * (float) Math.PI / 180.0F);
				float expectedSine = (float) Math.sin(angleInRadians);
				float expectedCosine = (float) Math.cos(angleInRadians);
				float actualSine = SinusCache.sin(angleInRadians, fastMath);
				float actualCosine = SinusCache.cos(angleInRadians, fastMath);
				assertEquals(expectedSine, actualSine, 1.5e-3, "Sine value mismatch at angle: " + i + " with fastMath: " + fastMath);
				assertEquals(expectedCosine, actualCosine, 1.5e-3, "Cosine value mismatch at angle: " + i + " with fastMath: " + fastMath);
			}
		}
	}
}
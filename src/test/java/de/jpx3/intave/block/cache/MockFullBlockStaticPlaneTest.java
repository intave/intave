package de.jpx3.intave.block.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockFullBlockStaticPlaneTest {

	@Test
	public void testHorizontalFill() {
		MockFullBlockStaticPlane mockFullBlockStaticPlane = new MockFullBlockStaticPlane();
		mockFullBlockStaticPlane.horizontalFill(4);

		for (int x = -16; x < 16; x++) {
			for (int z = -16; z < 16; z++) {
				assertTrue(mockFullBlockStaticPlane.isStone(x, 4, z), "Expected stone at (" + x + ", 4, " + z + ")");
				for (int y = -256; y < 256; y++) {
					if (y != 4) {
						assertFalse(mockFullBlockStaticPlane.isStone(x, y, z), "Expected no stone at (" + x + ", " + y + ", " + z + ")");
					}
				}
			}
		}
	}
}
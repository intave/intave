/*
 * Copyright 2026 Intave
 *
 * This software is licensed under the PolyForm Perimeter License 1.0.0.
 * You may use this software for any purpose, except for providing to
 * others any product that competes with the software.
 *
 * A copy of the license is available at:
 *   https://polyformproject.org/licenses/perimeter/1.0.0/
 */

package de.jpx3.intave.packet.wire.player.animation;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ArmAnimationWirePacketCodecsTest {
	@Test
	void unsupportedFieldsCannotBeAddedToPacketsThatDoNotCarryThem() {
		assertNull(new ArmAnimationWithoutHandWirePacket().hand());
		assertThrows(
				UnsupportedOperationException.class,
				() ->
						new ArmAnimationWithoutHandWirePacket()
								.setHand(de.jpx3.intave.share.PlayerHand.MAIN_HAND));
	}
}

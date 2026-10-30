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

package de.jpx3.intave.packet.wire.player.action;

import de.jpx3.intave.share.PlayerAction;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerActionWirePacketCodecsTest {
	@Test
	void entityActionSixMeansInventoryBeforeOffhandWasAdded() {
		PlayerActionWirePacket legacy =
				roundTrip(
						SERVERBOUND, "ENTITY_ACTION", 47, b -> b.writeBytes(new byte[] {12, 6, 0}));
		PlayerActionWirePacket modern =
				roundTrip(
						SERVERBOUND,
						"ENTITY_ACTION",
						107,
						b -> b.writeBytes(new byte[] {12, 6, 0}));
		assertEquals(PlayerAction.OPEN_INVENTORY, legacy.playerAction());
		assertEquals(PlayerAction.STOP_RIDING_JUMP, modern.playerAction());
	}
}

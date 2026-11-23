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

package de.jpx3.intave.packet.wire.entity.attach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class AttachEntityWirePacketCodecsTest {
	@Test
	void unsupportedFieldsCannotBeAddedToPacketsThatDoNotCarryThem() {
		assertFalse(new AttachEntityWirePacket.Protocol107().isMount());
		assertThrows(
				UnsupportedOperationException.class,
				() -> new AttachEntityWirePacket.Protocol107().setMount(true));
		AttachEntityWirePacket.Protocol47 legacyAttach =
				new AttachEntityWirePacket.Protocol47(1, 2, true);
		assertTrue(legacyAttach.isMount());
	}
}

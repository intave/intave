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

package de.jpx3.intave.packet.wire.tabcomplete.request;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import org.junit.jupiter.api.Test;

import static de.jpx3.intave.packet.PacketDirection.SERVERBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;

final class TabCompleteRequestWirePacketCodecsTest {
	@Test
	void tabCompletionKeepsOptionalTargetsAndTransactionIds() {
		roundTrip(
				SERVERBOUND,
				"TAB_COMPLETE",
				47,
				b -> {
					ByteBufStreamCodecs.VARINT_STRING.encode(b, "/help");
					b.writeBoolean(false);
				});
		roundTrip(
				SERVERBOUND,
				"TAB_COMPLETE",
				107,
				b -> {
					ByteBufStreamCodecs.VARINT_STRING.encode(b, "/help");
					b.writeBoolean(true).writeBoolean(true).writeLong(0);
				});
		roundTrip(
				SERVERBOUND,
				"TAB_COMPLETE",
				393,
				b -> {
					b.writeByte(42);
					ByteBufStreamCodecs.VARINT_STRING.encode(b, "/help");
				});
	}
}

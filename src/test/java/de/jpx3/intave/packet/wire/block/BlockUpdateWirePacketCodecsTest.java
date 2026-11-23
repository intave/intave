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

package de.jpx3.intave.packet.wire.block;

import de.jpx3.intave.codec.ByteBufStreamCodecs;
import de.jpx3.intave.packet.contract.PacketType;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class BlockUpdateWirePacketCodecsTest {
	@Test
	void singleChangesUseTheCorrectPositionLayoutInEverySupportedProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			if (protocol < 47) continue;
			SingleBlockChangeWirePacket.Base packet = roundTrip(CLIENTBOUND, PacketType.Server.BLOCK_CHANGE, protocol, input -> {
				// (-1, -2, 3): X/Y/Z before 1.14, X/Z/Y since 1.14.
				input.writeLong(protocol < 477 ? 0xfffffffff8000003L : 0xffffffc000003ffeL);
				input.writeBytes(new byte[] {(byte) 0xc5, (byte) 0xc6, 4}); // state 0x12345
			});
			assertEquals(protocol < 477 ? SingleBlockChangeWirePacket.Protocol47.class : SingleBlockChangeWirePacket.Protocol477.class, packet.getClass());
			assertUpdate(packet.blockUpdate(), -1, -2, 3, 0x12345);
			assertEquals(packet.blockPosition(), packet.blockUpdate().position());
			assertEquals(0x12345, packet.stateId());
			assertFalse(packet.couldHaveBeenMutated());
		}
	}

	@Test
	void singleChangesSerializeContractAndScalarMutations() {
		for (int protocol : new int[] {47, 404, 477, 777}) {
			SingleBlockChangeWirePacket.Base packet = WirePackets.createPlayPacket(PacketType.Server.BLOCK_CHANGE, protocol);
			packet.setBlockUpdate(new BlockUpdate(new BlockPosition(1, 2, 3), 20));
			packet.setBlockPosition(new BlockPosition(-33554432, -2048, 33554431));
			packet.setStateId(Integer.MAX_VALUE);
			assertTrue(packet.couldHaveBeenMutated());
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(CLIENTBOUND, PLAY, PacketType.Server.BLOCK_CHANGE, protocol, packet, output);
				SingleBlockChangeWirePacket.Base decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, output);
				assertUpdate(decoded.blockUpdate(), -33554432, -2048, 33554431, Integer.MAX_VALUE);
				assertFalse(decoded.couldHaveBeenMutated());
			} finally {
				output.release();
			}
		}
	}

	@Test
	void invalidSingleUpdatesCannotSilentlyLoseData() {
		SingleBlockChangeWirePacket.Base packet = new SingleBlockChangeWirePacket.Protocol477();
		assertThrows(IllegalArgumentException.class, () -> packet.setBlockUpdates(Collections.emptyList()));
		assertThrows(IllegalArgumentException.class, () -> packet.setBlockUpdates(Arrays.asList(packet.blockUpdate(), packet.blockUpdate())));
		assertThrows(NullPointerException.class, () -> packet.setBlockPosition(null));
		assertThrows(IllegalArgumentException.class, () -> packet.setStateId(-1));
		assertThrows(IllegalArgumentException.class, () -> packet.setBlockPosition(new BlockPosition(33554432, 0, 0)));
		assertThrows(IllegalArgumentException.class, () -> packet.setBlockPosition(new BlockPosition(0, 2048, 0)));
		assertEquals(BlockPosition.ORIGIN, packet.blockPosition());
		assertEquals(0, packet.stateId());
	}

	@Test
	void negativeSingleStateIdsAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeLong(0);
			ByteBufStreamCodecs.VARINT.encode(input, -1);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(SingleBlockChangeWirePacket.Protocol477.class, input));
		} finally {
			input.release();
		}
	}

	private static void assertUpdate(BlockUpdate update, int x, int y, int z, int stateId) {
		assertEquals(new BlockPosition(x, y, z), update.position());
		assertEquals(stateId, update.stateId());
	}
}

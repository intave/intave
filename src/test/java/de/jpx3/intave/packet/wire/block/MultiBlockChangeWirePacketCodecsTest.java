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
import de.jpx3.intave.packet.contract.immutable.MultiBlockChangePacket;
import de.jpx3.intave.packet.contract.mutable.MutableBlockUpdates;
import de.jpx3.intave.packet.contract.mutable.MutableMultiBlockChangePacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.BlockUpdate;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class MultiBlockChangeWirePacketCodecsTest {
	@Test
	void multiChangesUseTheCorrectHeadersAndRecordsInEverySupportedProtocol() {
		for (int protocol : registry(CLIENTBOUND).supportedProtocolVersions()) {
			if (protocol < 47) continue;
			MultiBlockChangePacket packet = roundTrip(CLIENTBOUND, PacketType.Server.MULTI_BLOCK_CHANGE, protocol, input -> {
				if (protocol < 751) {
					input.writeInt(-2).writeInt(2).writeByte(2);
					input.writeShort(0xff00).writeByte(0);
					input.writeShort(0x00ff).writeBytes(new byte[] {(byte) 0xc5, (byte) 0xc6, 4});
				} else {
					input.writeLong(0xfffff800002ffffcL); // section (-2, -4, 2)
					if (protocol < 763) input.writeBoolean(true);
					input.writeByte(2);
					ByteBufStreamCodecs.VARLONG.encode(input, 0xff0L);
					ByteBufStreamCodecs.VARLONG.encode(input, 0x1234500fL);
				}
			});
			assertEquals(2, packet.blockUpdates().size());
			assertUpdate(packet.blockUpdates().get(0), -17, protocol < 751 ? 0 : -64, 47, 0);
			assertUpdate(packet.blockUpdates().get(1), -32, protocol < 751 ? 255 : -49, 32, 0x12345);
			if (protocol < 751) {
				MultiBlockChangeWirePacket.Protocol47 chunk = assertInstanceOf(MultiBlockChangeWirePacket.Protocol47.class, packet);
				assertEquals(-2, chunk.chunkX());
				assertEquals(2, chunk.chunkZ());
			} else {
				assertEquals(new BlockPosition(-2, -4, 2), ((MultiBlockChangeWirePacket.Base) packet).sectionPosition());
				if (protocol < 763) {
					assertTrue(assertInstanceOf(MultiBlockChangeWirePacket.Protocol751.class, packet).suppressLightUpdates());
				} else {
					assertEquals(MultiBlockChangeWirePacket.Protocol763.class, packet.getClass());
				}
			}
			assertFalse(((MutableBlockUpdates) packet).couldHaveBeenMutated());
		}
	}

	@Test
	void emptyBatchesPreserveTheirNonzeroHeadersAndLightFlags() {
		for (int protocol : new int[] {47, 736, 751, 762, 763, 777}) {
			for (boolean suppress : new boolean[] {false, true}) {
				MultiBlockChangePacket packet = roundTrip(CLIENTBOUND, PacketType.Server.MULTI_BLOCK_CHANGE, protocol, input -> {
					if (protocol < 751) input.writeInt(-123).writeInt(456);
					else input.writeLong(0xfffff800002ffffcL);
					if (protocol >= 751 && protocol < 763) input.writeBoolean(suppress);
					input.writeByte(0);
				});
				assertTrue(packet.blockUpdates().isEmpty());
				if (packet instanceof MultiBlockChangeWirePacket.Protocol751) {
					assertEquals(suppress, ((MultiBlockChangeWirePacket.Protocol751) packet).suppressLightUpdates());
				}
			}
		}
	}

	@Test
	void sectionCoordinatesAndStateIdsRetainTheirFullBitWidths() {
		for (int protocol : new int[] {762, 763, 777}) {
			MultiBlockChangeWirePacket.Base packet = roundTrip(CLIENTBOUND, PacketType.Server.MULTI_BLOCK_CHANGE, protocol, input -> {
				input.writeLong(0x800001fffff80000L); // min X/Y, max Z
				if (protocol < 763) input.writeBoolean(false);
				input.writeByte(2);
				ByteBufStreamCodecs.VARLONG.encode(input, (long) Integer.MAX_VALUE << 12 | 0xfff);
				ByteBufStreamCodecs.VARLONG.encode(input, 0L);
			});
			assertEquals(new BlockPosition(-2097152, -524288, 2097151), packet.sectionPosition());
			assertUpdate(packet.blockUpdates().get(0), -33554417, -8388593, 33554431, Integer.MAX_VALUE);
			assertUpdate(packet.blockUpdates().get(1), -33554432, -8388608, 33554416, 0);
		}
	}

	@Test
	void multiChangesDeriveHeadersAndSerializeMutationsWithoutExposingMutableLists() {
		for (int protocol : new int[] {47, 736, 751, 762, 763, 777}) {
			MutableMultiBlockChangePacket packet = WirePackets.createPlayPacket(PacketType.Server.MULTI_BLOCK_CHANGE, protocol);
			BlockUpdate update = new BlockUpdate(new BlockPosition(-17, 63, 47), 1234567);
			List<BlockUpdate> updates = new ArrayList<>(Collections.singletonList(update));
			packet.setBlockUpdates(updates);
			updates.clear();
			assertEquals(1, packet.blockUpdates().size());
			assertThrows(UnsupportedOperationException.class, () -> packet.blockUpdates().clear());
			if (packet instanceof MultiBlockChangeWirePacket.Protocol751) {
				((MultiBlockChangeWirePacket.Protocol751) packet).setSuppressLightUpdates(true);
			}
			ByteBuf output = Unpooled.buffer();
			try {
				WirePackets.encodePacket(CLIENTBOUND, PLAY, PacketType.Server.MULTI_BLOCK_CHANGE, protocol, packet, output);
				MutableMultiBlockChangePacket decoded = WirePackets.decodePacket(CLIENTBOUND, PLAY, protocol, output);
				assertUpdate(decoded.blockUpdates().get(0), -17, 63, 47, 1234567);
				assertFalse(decoded.couldHaveBeenMutated());
				if (decoded instanceof MultiBlockChangeWirePacket.Base) {
					assertEquals(new BlockPosition(-2, 3, 2), ((MultiBlockChangeWirePacket.Base) decoded).sectionPosition());
				}
				if (decoded instanceof MultiBlockChangeWirePacket.Protocol751) {
					assertTrue(((MultiBlockChangeWirePacket.Protocol751) decoded).suppressLightUpdates());
				}
			} finally {
				output.release();
			}
		}
	}

	@Test
	void batchesRejectMixedRegionsAndOutOfRangeCoordinatesWithoutChangingTheirContents() {
		BlockUpdate origin = new BlockUpdate(BlockPosition.ORIGIN, 0);
		MultiBlockChangeWirePacket.Base section = new MultiBlockChangeWirePacket.Protocol763();
		section.setBlockUpdates(Collections.singletonList(origin));
		for (BlockPosition position : new BlockPosition[] {
			new BlockPosition(16, 0, 0), new BlockPosition(0, 16, 0), new BlockPosition(0, 0, 16)
		}) {
			assertThrows(IllegalArgumentException.class, () -> section.setBlockUpdates(Arrays.asList(origin, new BlockUpdate(position, 1))));
		}
		assertThrows(IllegalArgumentException.class, () -> section.setBlockUpdates(Collections.singletonList(new BlockUpdate(new BlockPosition(33554432, 0, 0), 1))));
		assertThrows(IllegalArgumentException.class, () -> section.setBlockUpdates(Collections.singletonList(new BlockUpdate(new BlockPosition(0, 8388608, 0), 1))));
		assertSame(origin, section.blockUpdates().get(0));
		assertEquals(BlockPosition.ORIGIN, section.sectionPosition());

		MultiBlockChangeWirePacket.Protocol47 chunk = new MultiBlockChangeWirePacket.Protocol47();
		chunk.setBlockUpdates(Collections.singletonList(origin));
		for (BlockPosition position : new BlockPosition[] {
			new BlockPosition(16, 0, 0), new BlockPosition(0, 0, -1), new BlockPosition(0, -1, 0), new BlockPosition(0, 256, 0)
		}) {
			assertThrows(IllegalArgumentException.class, () -> chunk.setBlockUpdates(Arrays.asList(origin, new BlockUpdate(position, 1))));
		}
		assertSame(origin, chunk.blockUpdates().get(0));
	}

	@Test
	void malformedCountsTruncatedRecordsAndOversizedStateIdsAreRejected() {
		ByteBuf input = Unpooled.buffer();
		try {
			input.writeLong(0);
			ByteBufStreamCodecs.VARINT.encode(input, -1);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MultiBlockChangeWirePacket.Protocol763.class, input));
			input.clear().writeLong(0).writeByte(2).writeByte(0);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MultiBlockChangeWirePacket.Protocol763.class, input));
			input.clear().writeLong(0).writeByte(1).writeByte(0x80);
			assertThrows(IndexOutOfBoundsException.class, () -> decodePayload(MultiBlockChangeWirePacket.Protocol763.class, input));
			input.clear().writeLong(0).writeByte(1);
			ByteBufStreamCodecs.VARLONG.encode(input, ((long) Integer.MAX_VALUE + 1) << 12);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MultiBlockChangeWirePacket.Protocol763.class, input));
			input.clear().writeInt(0).writeInt(0).writeByte(1).writeShort(0);
			assertThrows(IllegalArgumentException.class, () -> decodePayload(MultiBlockChangeWirePacket.Protocol47.class, input));
		} finally {
			input.release();
		}
	}

	private static void assertUpdate(BlockUpdate update, int x, int y, int z, int stateId) {
		assertEquals(new BlockPosition(x, y, z), update.position());
		assertEquals(stateId, update.stateId());
	}
}

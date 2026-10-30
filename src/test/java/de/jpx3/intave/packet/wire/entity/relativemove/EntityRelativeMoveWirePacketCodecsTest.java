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

package de.jpx3.intave.packet.wire.entity.relativemove;

import de.jpx3.intave.packet.contract.mutable.MutableEntityRelativeMovePacket;
import de.jpx3.intave.packet.wire.WirePackets;
import de.jpx3.intave.share.RawVector3d;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.jpx3.intave.packet.ConnectionState.PLAY;
import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.registry;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class EntityRelativeMoveWirePacketCodecsTest {
	@Test
	void relativeMovesSwitchFromBytesToShortsThenSteppedPaths() {
		for (String name : new String[] {"REL_ENTITY_MOVE", "REL_ENTITY_MOVE_LOOK"}) {
			for (int protocol : new int[] {47, 107, 777}) {
				de.jpx3.intave.packet.contract.EntityRelativeMovePacket packet =
						roundTrip(
								CLIENTBOUND,
								name,
								protocol,
								b -> {
									b.writeByte(42);
									if (protocol >= 777) b.writeByte(1);
									if (protocol == 47) b.writeByte(32).writeByte(-64).writeByte(1);
									else b.writeShort(4096).writeShort(-8192).writeShort(128);
									if (name.endsWith("LOOK")) b.writeByte(64).writeByte(16);
									if (protocol < 777) b.writeBoolean(true);
								});
				assertEquals(1, packet.steps().size());
				assertEquals(1, packet.steps().get(0).delta().x());
				assertEquals(-2, packet.steps().get(0).delta().y());
			}
		}
		EntityRelativeMoveWirePacket stepped =
				roundTrip(
						CLIENTBOUND,
						"REL_ENTITY_MOVE",
						777,
						b -> {
							b.writeByte(42).writeByte(5);
							b.writeByte(2).writeShort(4096).writeShort(0).writeShort(0);
							b.writeByte(3).writeShort(0).writeShort(4096).writeShort(0);
						});
		assertEquals(2, stepped.steps().size());
		assertEquals(3, stepped.steps().get(1).delayTicks());
	}

	@Test
	void relativeMoveCodecsPreserveSignedDeltaBounds() {
		for (String name : new String[] {"REL_ENTITY_MOVE", "REL_ENTITY_MOVE_LOOK"}) {
			for (int protocol : new int[] {47, 107, 777}) {
				MutableEntityRelativeMovePacket packet =
						roundTrip(
								CLIENTBOUND,
								name,
								protocol,
								b -> {
									b.writeByte(42);
									if (protocol >= 777) b.writeByte(1);
									if (protocol == 47)
										b.writeByte(-128).writeByte(127).writeByte(-1);
									else b.writeShort(-32768).writeShort(32767).writeShort(-1);
									if (name.endsWith("LOOK")) b.writeByte(-128).writeByte(127);
									if (protocol < 777) b.writeBoolean(true);
								});
				RawVector3d delta = packet.steps().getFirst().delta();
				assertEquals(protocol == 47 ? -4 : -8, delta.x());
				assertEquals(protocol == 47 ? 127 / 32.0 : 32767 / 4096.0, delta.y());
				assertEquals(protocol == 47 ? -1 / 32.0 : -1 / 4096.0, delta.z());
			}
		}
	}

	@Test
	void relativeMoveCodecsRejectOutOfRangeAndNonfiniteDeltas() {
		for (String name : new String[] {"REL_ENTITY_MOVE", "REL_ENTITY_MOVE_LOOK"}) {
			for (int protocol : new int[] {47, 107, 777}) {
				int id = registry(CLIENTBOUND).idOf(PLAY, name, protocol);
				MutableEntityRelativeMovePacket packet =
						WirePackets.createPacket(CLIENTBOUND, PLAY, id, protocol);
				for (double value :
						new double[] {
							protocol == 47 ? 4 : 8, -9, Double.NaN, Double.POSITIVE_INFINITY
						}) {
					packet.setSteps(
							List.of(
									new EntityRelativeMoveWirePacket.Step(
											new RawVector3d(0, 0, value), 0)));
					ByteBuf output = Unpooled.buffer().writeByte(0x55);
					try {
						assertThrows(
								IllegalArgumentException.class,
								() ->
										WirePackets.encodePacket(
												CLIENTBOUND, PLAY, name, protocol, packet, output));
						assertEquals(1, output.writerIndex());
						assertEquals(0x55, output.getUnsignedByte(0));
					} finally {
						output.release();
					}
				}
			}
		}
	}
}

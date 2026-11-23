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
import de.jpx3.intave.packet.contract.immutable.BlockBreakAckPacket;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.PlayerDigAction;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static de.jpx3.intave.packet.PacketDirection.CLIENTBOUND;
import static de.jpx3.intave.packet.contract.PacketType.Server.BLOCK_BREAK_ACK;
import static de.jpx3.intave.packet.wire.WirePacketTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class BlockBreakAckWirePacketTest {
  @TestFactory
  Stream<DynamicTest> roundTripsEverySupportedProtocol() {
    return registry(CLIENTBOUND).supportedProtocolVersions().stream()
      .filter(protocol -> protocol >= 498 && protocol < 759)
      .map(protocol -> DynamicTest.dynamicTest("digging acknowledgement " + protocol, () -> {
        for (int action = 0; action < 7; action++) {
          int actionId = action;
          for (boolean successful : new boolean[]{false, true}) {
            BlockBreakAckPacket packet = roundTrip(CLIENTBOUND, BLOCK_BREAK_ACK, protocol, output -> {
              output.writeLong(0xffffffc000003ffeL); // (-1, -2, 3), X/Z/Y packing
              ByteBufStreamCodecs.VARINT.encode(output, 12345);
              ByteBufStreamCodecs.VARINT.encode(output, actionId);
              output.writeBoolean(successful);
            });
            assertEquals(new BlockPosition(-1, -2, 3), packet.blockPosition());
            assertEquals(12345, packet.blockUpdate().stateId());
            assertEquals(PlayerDigAction.values()[action], packet.action());
            assertEquals(successful, packet.successful());
            assertEquals(1, packet.blockUpdates().size());
          }
        }
      }));
  }

  @Test
  void rejectsInvalidActionsStatesAndTruncation() {
    for (int action : new int[]{-1, 7, 127}) {
      var input = Unpooled.buffer();
      try {
        input.writeLong(0);
        ByteBufStreamCodecs.VARINT.encode(input, 1);
        ByteBufStreamCodecs.VARINT.encode(input, action);
        input.writeBoolean(true);
        assertThrows(IllegalArgumentException.class, () -> decodePayload(BlockBreakAckWirePacket.class, input));
      } finally {
        input.release();
      }
    }
    var input = Unpooled.buffer();
    try {
      input.writeLong(0);
      ByteBufStreamCodecs.VARINT.encode(input, -1);
      assertThrows(IllegalArgumentException.class, () -> decodePayload(BlockBreakAckWirePacket.class, input));
      input.clear().writeLong(0).writeByte(1).writeByte(0);
      assertThrows(IndexOutOfBoundsException.class, () -> decodePayload(BlockBreakAckWirePacket.class, input));
    } finally {
      input.release();
    }
  }

  @Test
  void onlyAppliesBetweenIntroductionAndSequenceAcknowledgements() {
    var packet = new BlockBreakAckWirePacket();
    assertFalse(packet.appliesTo(490));
    assertTrue(packet.appliesTo(498));
    assertTrue(packet.appliesTo(758));
    assertFalse(packet.appliesTo(759));
  }
}

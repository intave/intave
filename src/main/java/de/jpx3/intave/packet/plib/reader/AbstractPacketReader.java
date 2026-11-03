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

package de.jpx3.intave.packet.plib.reader;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import de.jpx3.intave.IntaveControl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPacketReader implements PacketReader {
  private static final Map<PacketType, AtomicLong> MISSING_FLUSHES_BY_TYPE = new HashMap<>();

  private PacketContainer packet;

  @Override
  public void enter(PacketContainer packet) {
    if (this.packet != null) {
      long val = MISSING_FLUSHES_BY_TYPE.computeIfAbsent(packet.getType(), packetType -> new AtomicLong()).incrementAndGet();
      if (val < 5 && IntaveControl.NOTIFY_MISSING_PACKET_FLUSHES) {
        System.out.println("Missing flush for packet " + packet.getType() + " (" + val + ")");
        Thread.dumpStack();
      }
    }
    this.packet = packet;
  }

  @Override
  public void flush() {
  }

  @Override
  public void release() {
    packet = null;
  }

  @Override
  public void releaseSafe() {
    if (packet == null) {
      return;
    }
    release();
  }

  public PacketContainer packet() {
    return packet;
  }
}

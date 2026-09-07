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

package de.jpx3.intave.module.linker.packet.pe;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;

/**
 * Buffer plumbing for code that has to take an outbound packet's bytes away from PacketEvents.
 */
public final class PacketEventsBuffers {

  private PacketEventsBuffers() {
  }

  /**
   * Brings the event's buffer back to the state {@code getFullBufferClone()} needs, and is the step
   * that has to happen before any sandwich takes its copy.
   * <p>
   * Two separate reasons, both of which corrupt the copy on their own:
   * <ol>
   *   <li><b>The payload has already been read away.</b> Building a wrapper over a
   *   {@code PacketSendEvent} consumes the buffer, and {@code getFullBufferClone()} copies
   *   {@code readerIndex..writerIndex}. PacketEvents restores that index only <em>between</em>
   *   listeners, never inside one, so a handler that decoded the packet through a view and then
   *   asked for the clone would get a bare packet id varint with no payload.</li>
   *   <li><b>Setter writes have not landed in the buffer.</b> PacketEvents re-encodes a modified
   *   wrapper after the listener chain returns, and only when the event was not cancelled. A
   *   sandwich cancels, so that rewrite never runs and a copy would carry the original values - for
   *   a handler that rewrote the packet, that is the pre-rewrite payload going onto the wire.</li>
   * </ol>
   * This performs exactly the rewrite PacketEvents itself performs, in the same order, then puts the
   * reader index back on the payload boundary the listener was handed. Idempotent: running it twice
   * clears and rewrites the same bytes.
   *
   * @return true when the buffer was rewritten; false when the handler never decoded a wrapper, in
   * which case the buffer was never disturbed and the clone is already correct.
   */
  public static boolean encodeIntoEventBuffer(PacketSendEvent event) {
    if (event == null) {
      return false;
    }
    PacketWrapper<?> wrapper = event.getLastUsedWrapper();
    Object buffer = event.getByteBuf();
    if (wrapper == null || buffer == null) {
      return false;
    }
    wrapper.setBuffer(buffer);
    ByteBufHelper.clear(buffer);
    ByteBufHelper.writeVarInt(buffer, event.getPacketId());
    int payloadStart = ByteBufHelper.writerIndex(buffer);
    wrapper.write();
    ByteBufHelper.readerIndex(buffer, payloadStart);
    return true;
  }
}

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

package de.jpx3.intave.module.feedback;

/**
 * One outbound packet parked by the PacketEvents half of {@link PacketDelayer}, as encoded bytes.
 * <p>
 * The ProtocolLib half parks the raw NMS packet object {@code PacketContainer#getHandle} returns and
 * both live in the same queues ({@code ConnectionMetadata#enqueuedPackets()} and
 * {@code delayedPackets()}, both typed {@code Object}). This wrapper exists so the drain site can
 * tell the two apart by type rather than by guessing: an NMS packet object and a netty buffer are
 * both {@code Object}, and handing a buffer to {@code PacketContainer.fromPacket} would throw at the
 * worst possible moment - while a player's packets are being released. See
 * {@link PacketDelayer#enqueueOutgoingPackets(com.github.retrooper.packetevents.event.PacketSendEvent, org.bukkit.entity.Player)}
 * for why the queue is shared rather than duplicated per engine.
 * <p>
 * <b>Ownership.</b> {@link #buffer()} is what {@code ProtocolPacketEvent#getFullBufferClone()} handed
 * out: a freshly allocated, unpooled heap buffer holding {@code [packet id varint][payload]}, owned
 * by nobody but this object. It may be written <em>exactly once</em>, because
 * {@code ProtocolManager#sendPacketSilently(channel, buffer)} hands it to netty, which releases it
 * after the write (and releases it itself when the channel is already closed). One instance
 * therefore stands for one re-emission: a packet that has to go out twice gets two clones, never one
 * clone parked twice.
 * <p>
 * A parked buffer that is never flushed - the player disconnects while the queue is full - needs no
 * cleanup. The clone comes from {@code Unpooled.buffer()}, so it is a plain heap buffer with a
 * {@code byte[]} behind it: dropping the reference along with the user's metadata gives it to the
 * garbage collector. There is no pool to return it to and no direct memory to free.
 */
public final class PacketEventsDelayedBuffer {

  private final Object buffer;

  public PacketEventsDelayedBuffer(Object buffer) {
    this.buffer = buffer;
  }

  /** @return the encoded packet, ready to be written by {@code sendPacketSilently}. */
  public Object buffer() {
    return buffer;
  }
}

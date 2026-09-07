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

package de.jpx3.intave.packet.view;

import com.comphenix.protocol.events.PacketEvent;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import de.jpx3.intave.module.linker.packet.pe.PacketEventWrapper;
import org.bukkit.entity.Player;

/**
 * {@link FeedbackHandle} backed by PacketEvents.
 * <p>
 * Reports no bundling target, because a PacketEvents event carries an encoded buffer rather than a
 * ProtocolLib {@code PacketContainer} and {@code FeedbackSender} builds and sends the bundle purely
 * through ProtocolLib. A null target is not a degraded mode invented here: it is the same input the
 * feedback module receives on every server below 1.19.4 and whenever
 * {@code check.physics.no-bundling} is enabled, and it makes the module take its plain branch -
 * the transaction packet is sent on its own and the observed packet stays in the pipeline
 * untouched, uncancelled and unmodified.
 * <p>
 * The handle keeps a reference to the event only to answer {@link #player()}; it never cancels it,
 * never re-encodes it and never reads its buffer, so constructing one has no effect on the packet.
 */
public final class PacketEventsFeedbackHandle implements FeedbackHandle {

  @SuppressWarnings("rawtypes")
  private final ProtocolPacketEvent event;

  @SuppressWarnings("rawtypes")
  private PacketEventsFeedbackHandle(ProtocolPacketEvent event) {
    this.event = event;
  }

  /** @return a handle over an inbound packet, or null when there is no event to attach to. */
  public static PacketEventsFeedbackHandle of(PacketReceiveEvent event) {
    return event == null ? null : new PacketEventsFeedbackHandle(event);
  }

  /** @return a handle over an outbound packet, or null when there is no event to attach to. */
  public static PacketEventsFeedbackHandle of(PacketSendEvent event) {
    return event == null ? null : new PacketEventsFeedbackHandle(event);
  }

  /**
   * @return a handle over the packet the given subscription wrapper describes, or null when there
   * is no event to attach to. Lets a subscription that takes the direction neutral
   * {@link PacketEventWrapper} request feedback without unwrapping it first.
   */
  public static PacketEventsFeedbackHandle of(PacketEventWrapper event) {
    return event == null ? null : new PacketEventsFeedbackHandle(event.nativeEvent());
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public PacketEvent bundleTarget() {
    // No ProtocolLib container exists for this packet; see the class javadoc.
    return null;
  }
}

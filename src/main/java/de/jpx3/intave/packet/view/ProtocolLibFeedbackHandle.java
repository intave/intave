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
import org.bukkit.entity.Player;

/**
 * {@link FeedbackHandle} backed by ProtocolLib.
 * <p>
 * Hands the wrapped event straight back as the bundling target, so a feedback request routed
 * through this handle reaches {@code FeedbackSender} with exactly the argument the old
 * {@code PacketEvent} taking signatures passed. Nothing about the ProtocolLib feedback path
 * changes: the packet is still bundled on 1.19.4+, still cancelled and re-sent inside the bundle,
 * and still left untouched everywhere else.
 */
public final class ProtocolLibFeedbackHandle implements FeedbackHandle {

  private final PacketEvent event;

  private ProtocolLibFeedbackHandle(PacketEvent event) {
    this.event = event;
  }

  /** @return a handle over the given event, or null when there is no event to attach to. */
  public static ProtocolLibFeedbackHandle of(PacketEvent event) {
    return event == null ? null : new ProtocolLibFeedbackHandle(event);
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public PacketEvent bundleTarget() {
    return event;
  }
}

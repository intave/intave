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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

/**
 * {@link ChatTextView} backed by ProtocolLib.
 * <p>
 * No pooled reader exists for these packets: both the chat packet and the tab complete request
 * keep the text in string field 0, so the view goes straight to the structure modifier, which is
 * exactly what the filters did before this seam existed.
 */
public final class ProtocolLibChatTextView implements ChatTextView {

  private static final int TEXT_FIELD = 0;

  private final PacketEvent event;

  public ProtocolLibChatTextView(PacketEvent event) {
    this.event = event;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public String text() {
    return event.getPacket().getStrings().readSafely(TEXT_FIELD);
  }

  @Override
  public void setText(String text) {
    event.getPacket().getStrings().writeSafely(TEXT_FIELD, text);
  }

  @Override
  public void blankPayload() {
    // A fresh container of the same type: every field falls back to its default, so a listener
    // further down the chain reads nothing of what the player actually sent.
    event.setPacket(new PacketContainer(event.getPacketType()));
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public void release() {
    // Writes go straight into the live packet container, so there is nothing to flush and no
    // pooled reader to hand back.
  }
}

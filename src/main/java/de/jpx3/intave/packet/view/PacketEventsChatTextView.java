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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTabComplete;
import org.bukkit.entity.Player;

/**
 * {@link ChatTextView} backed by PacketEvents.
 * <p>
 * PacketEvents models the two packets this family covers as separate wrappers, so exactly one of
 * them is non-null on any given view and it decides which accessor pair carries the text.
 * <p>
 * The text is decoded once at construction rather than on every read, because every PacketEvents
 * getter re-reads the packet buffer and the command filter reads the text at least once per
 * packet. Writes are buffered behind a dirty flag and flushed in {@link #release()}, so a packet
 * the filter did not rewrite is never re-encoded.
 */
public final class PacketEventsChatTextView implements ChatTextView {

  private final PacketReceiveEvent event;

  /** Exactly one of the two wrappers is non-null; it owns the text field. */
  private final WrapperPlayClientChatMessage chatWrapper;
  private final WrapperPlayClientTabComplete tabCompleteWrapper;

  private String text;
  private boolean dirty;

  private PacketEventsChatTextView(
    PacketReceiveEvent event,
    WrapperPlayClientChatMessage chatWrapper,
    WrapperPlayClientTabComplete tabCompleteWrapper,
    String text
  ) {
    this.event = event;
    this.chatWrapper = chatWrapper;
    this.tabCompleteWrapper = tabCompleteWrapper;
    this.text = text;
  }

  /** @return a view over the event, or null when the packet carries no single text field. */
  public static PacketEventsChatTextView of(PacketReceiveEvent event) {
    PacketTypeCommon type = event.getPacketType();
    if (type == PacketType.Play.Client.CHAT_MESSAGE) {
      WrapperPlayClientChatMessage wrapper = new WrapperPlayClientChatMessage(event);
      return new PacketEventsChatTextView(event, wrapper, null, wrapper.getMessage());
    }
    if (type == PacketType.Play.Client.TAB_COMPLETE) {
      WrapperPlayClientTabComplete wrapper = new WrapperPlayClientTabComplete(event);
      return new PacketEventsChatTextView(event, null, wrapper, wrapper.getText());
    }
    return null;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public String text() {
    return text;
  }

  @Override
  public void setText(String text) {
    this.text = text;
    this.dirty = true;
  }

  @Override
  public void blankPayload() {
    // PacketEvents has no container swap the way ProtocolLib does - a wrapper writes into the
    // packet it was decoded from. Emptying the text field is the same observable result: the
    // caller cancels the packet immediately after, so this only matters if it ever were forwarded.
    setText("");
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    if (chatWrapper != null) {
      chatWrapper.setMessage(text);
    } else {
      tabCompleteWrapper.setText(text);
    }
    event.markForReEncode(true);
    dirty = false;
  }
}

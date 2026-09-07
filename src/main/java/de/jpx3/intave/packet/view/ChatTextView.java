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

import org.bukkit.entity.Player;

/**
 * Engine neutral view of an inbound packet whose payload is, for Intave's purposes, a single line
 * of text the player typed.
 * <p>
 * Two client packets fall into this family and the filters treat them identically: the chat packet
 * (which carries commands too, since a command is a chat line starting with a slash) and the tab
 * complete request (which carries the partial line the client wants completed). Both are read and
 * rewritten through the same single text field, so one view covers both rather than forcing a
 * near-empty family per packet.
 * <p>
 * The command filter reads and rewrites the text; the Log4J filter reads it and, on a hit, blanks
 * the payload before cancelling. Nothing else about either packet - signature data, transaction id,
 * looked-at block - is exposed, because nothing in the filter layer reads it.
 */
public interface ChatTextView {

  Player player();

  /**
   * @return the text the packet carries, or null when the packet has no text field to read. The
   * ProtocolLib path reads the field defensively rather than throwing, so a malformed packet ends
   * the filter early instead of aborting the whole listener chain.
   */
  String text();

  /** Rewrites the text field. Takes effect on {@link #release()} where the engine needs it to. */
  void setText(String text);

  /**
   * Drops the payload so nothing further down the chain can act on the text.
   * <p>
   * The caller cancels the packet in the same breath; this is the belt to that suspenders, and it
   * is what the ProtocolLib path has always done by swapping in an empty packet container.
   */
  void blankPayload();

  void setCancelled(boolean cancelled);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

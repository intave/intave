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

import io.netty.buffer.ByteBuf;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of an inbound custom payload (plugin message) packet.
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: the payload consumers only ever ask for the
 * channel tag, the raw remaining bytes and - for the client brand - the legacy length prefixed
 * string, so those are the only operations exposed here.
 */
public interface PayloadInView {

  Player player();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  /**
   * @return the payload channel with a leading {@code minecraft:} namespace stripped, so legacy
   * ("MC|Brand") and modern ("brand") channels compare the same way.
   */
  String tag();

  /**
   * @return the payload body positioned after the channel name. The buffer is owned by the packet;
   * callers mark and reset the reader index rather than releasing it.
   */
  ByteBuf readBytes();

  /**
   * Reads the payload as a string preceded by a single length byte, as the legacy client brand
   * channel encodes it.
   */
  String readStringWithExtraByte();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

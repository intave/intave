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
 * Engine neutral view of the outbound tab complete packet, as the filters use it: a flat array of
 * completion strings that entries can be dropped from.
 * <p>
 * <b>Scope, deliberately narrow.</b> Only the pre 1.13 form of this packet is exposed. Up to 1.12
 * the packet is literally a {@code String[]} of matches; from 1.13 on it carries Brigadier
 * suggestions with a transaction id, a replaced range and per entry tooltips. The ProtocolLib path
 * has only ever read the legacy string array field - on 1.13 and above that read yields null and
 * both filters that use this family fall through without touching anything - so this view reports
 * "no matches" above 1.12 as well. Widening the filter to modern suggestions would be a behaviour
 * change, not a port.
 */
public interface TabCompleteView {

  Player player();

  /**
   * @return the completion strings the packet carries, or null when it carries none in the form
   * the filters understand (see the scope note on this interface).
   */
  String[] matches();

  /** Replaces the completion strings. Takes effect on {@link #release()} where needed. */
  void setMatches(String[] matches);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

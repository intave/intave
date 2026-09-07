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
 * Engine neutral view of an outgoing chat packet.
 * <p>
 * Intave only ever asks one question about this packet: is it the action bar slot? The action bar
 * display suppresses foreign action bar text while a staff member watches another player, and
 * nothing else of the packet is read. Both backends encode that answer differently - ProtocolLib
 * exposes the legacy position byte plus the chat type enum, PacketEvents folds both into the chat
 * message's type - so the view exposes the answer rather than the field.
 */
public interface ChatOutView {

  Player player();

  /** @return true when the packet targets the action bar (legacy position 2 / {@code GAME_INFO}). */
  boolean isActionBar();

  void setCancelled(boolean cancelled);
}

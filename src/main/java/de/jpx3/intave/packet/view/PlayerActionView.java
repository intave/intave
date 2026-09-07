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

import de.jpx3.intave.packet.converter.PlayerAction;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of an inbound entity action packet (sneak, sprint, elytra, horse jump).
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: the consumers of this packet only ever ask
 * for the action itself and, in the movement dispatcher, cancel the packet when a sneak toggle is
 * being suppressed. Those are the only operations exposed so both backends can satisfy it without
 * leaking engine specific types.
 */
public interface PlayerActionView {

  Player player();

  /** @return the normalised action, never null. */
  PlayerAction playerAction();

  void setCancelled(boolean cancelled);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

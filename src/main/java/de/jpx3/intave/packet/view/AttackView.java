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
 * Engine neutral view of an inbound entity interaction packet (attack / interact / interact at).
 * <p>
 * Mirrors {@link MovementView}: the combat path only needs the target entity and the kind of
 * interaction, so those are the only operations exposed. Both backends normalise the same way,
 * including the newer protocol versions where the client sends attacks on their own packet
 * instead of as an action flag.
 */
public interface AttackView {

  Player player();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  /** @return the interacted entity's runtime id. */
  int entityId();

  /** @return true when the interaction is an attack rather than a right click. */
  boolean isAttackPacket();

  /** @return true for the "interact at" variant, which carries a hit vector. */
  boolean isSecondary();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

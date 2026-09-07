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
 * Engine neutral view of the target field of an inbound entity interaction packet, with write
 * access.
 * <p>
 * This is deliberately not {@link AttackView}. That family is read only, because the combat path
 * only ever observes interactions; the entity tracker instead rewrites the target of an
 * interaction aimed at a decoy entity back onto the entity the decoy stands in for, before any
 * later subscription reads it. Exposing a setter on {@link AttackView} would hand a write handle
 * to every combat check that takes one, so the write lives in its own single purpose family.
 */
public interface EntityInteractIdView {

  Player player();

  /** @return the interacted entity's runtime id, or null when the packet carries none. */
  Integer entityId();

  /** Redirects the interaction onto another entity. */
  void setEntityId(int entityId);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

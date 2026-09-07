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
 * Engine neutral view of an outbound entity status packet.
 * <p>
 * Entity status is the server's one byte "this entity just did something" channel; the entity
 * tracker watches it for status 3, the death animation, which is how it learns an entity died
 * without waiting for the metadata update. Only the addressed entity and that byte are exposed.
 * <p>
 * Both accessors are boxed because the ProtocolLib backend reads structure fields that can be
 * absent on a malformed or unexpectedly shaped packet, and the tracker's null handling was written
 * around that.
 */
public interface EntityStatusView {

  Player player();

  /** @return the runtime id of the entity the status describes, or null when the packet has none. */
  Integer entityId();

  /** @return the status byte, or null when the packet carries none. Status 3 is the death animation. */
  Byte status();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

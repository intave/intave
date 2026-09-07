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
 * Engine neutral view of the rotation carried by an inbound look packet.
 * <p>
 * {@link MovementView} already exposes {@link MovementView#yaw()} and {@link MovementView#pitch()},
 * but only for reading: the movement path never writes an angle back. The protocol scanner does -
 * it clamps an out of range pitch to zero before the server sees it - so that single write is the
 * reason this narrower family exists alongside the movement one.
 */
public interface RotationView {

  Player player();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  float yaw();

  float pitch();

  /** Replaces the pitch the client transmitted. */
  void setPitch(float pitch);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

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

import de.jpx3.intave.share.Position;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of an inbound movement packet.
 * <p>
 * The movement path is the largest consumer of packet data in Intave and was written directly
 * against ProtocolLib types. This interface is the seam that lets the same logic run on either
 * ProtocolLib or PacketEvents: it exposes exactly the operations the movement handler performs,
 * nothing more, so both backends can satisfy it without leaking engine specific types.
 */
public interface MovementView {

  Player player();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  /** @return true for the vehicle move packet, which carries the boat position rather than the player's. */
  boolean isVehicleMove();

  /** @return true for the combined position and rotation packet. */
  boolean isPositionLook();

  boolean hasMovement();

  boolean hasRotation();

  double positionX();

  double positionY();

  double positionZ();

  float yaw();

  float pitch();

  boolean onGround();

  boolean anyNaNOrInfiniteValue();

  void setPosition(Position position);

  void setOnGround(boolean onGround);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

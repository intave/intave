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

import de.jpx3.intave.share.Motion;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of an outbound entity velocity packet.
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: only the operations the three velocity
 * consumers actually perform are exposed - reading the target entity, reading the knockback and
 * rewriting it (knockback nerfing, setback velocity override) plus cancelling the packet outright.
 * The wire encoding differs per protocol version (a vector on modern versions, three fixed point
 * integers on legacy ones); both backends normalise to blocks per tick so the consumers never see
 * that difference.
 */
public interface EntityVelocityView {

  Player player();

  /** @return the runtime id of the entity the velocity applies to. */
  int entityId();

  double motionX();

  double motionY();

  double motionZ();

  /** @return a fresh, freely mutable snapshot of the knockback in blocks per tick. */
  Motion motion();

  void setMotionX(double motionX);

  void setMotionZ(double motionZ);

  void setMotion(Motion motion);

  /**
   * @return true when the engine hands out this packet for observation only, so cancelling it
   * would be ignored.
   */
  boolean readOnly();

  void setCancelled(boolean cancelled);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

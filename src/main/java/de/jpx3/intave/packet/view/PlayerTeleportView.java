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

import de.jpx3.intave.packet.Relative;
import de.jpx3.intave.share.Motion;
import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Engine neutral view of the outbound player position packet (the teleport instruction).
 * <p>
 * Mirrors {@link MovementView} on the outbound side: only the operations the teleport path
 * actually performs are exposed. The single consumer resolves relative coordinates against the
 * user's last verified position, rewrites the resolved absolutes back into the packet, strips the
 * positional relative flags it consumed, and records the teleport id so the confirmation can be
 * matched later.
 * <p>
 * The wire shape of this packet changed twice (a teleport id was added in 1.9, a dismount flag in
 * 1.17, and 1.21.3 folded position, delta and rotation into one structure); both backends
 * normalise those differences so the consumer never sees them.
 */
public interface PlayerTeleportView {

  Player player();

  double positionX();

  double positionY();

  double positionZ();

  void setPositionX(double positionX);

  void setPositionY(double positionY);

  void setPositionZ(double positionZ);

  float yaw();

  float pitch();

  /**
   * @return the relative flags of this teleport, as a mutable set the consumer may strip entries
   * from before handing it back to {@link #setFlags(Set)}.
   */
  Set<Relative> flags();

  void setFlags(Set<Relative> flags);

  /**
   * @return the delta motion carried by the teleport on protocols that encode one, otherwise a
   * zero motion. Only read when one of the delta relative flags is set.
   */
  Motion motion();

  /**
   * @return the dismount vehicle flag, or false on protocol versions that do not carry one.
   */
  boolean dismountVehicle();

  /**
   * @return the teleport id the client has to echo back. Only defined from 1.9 onwards, so the
   * consumer must not read it on older protocols.
   */
  int teleportId();

  /**
   * Writes back any pending modification without releasing the view, so a copy of the packet taken
   * afterwards observes the rewritten values.
   */
  void flush();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

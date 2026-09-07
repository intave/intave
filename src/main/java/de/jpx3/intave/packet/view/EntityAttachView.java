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
 * Engine neutral view of the two outbound packets that describe who is riding what.
 * <p>
 * Minecraft changed the shape of this information in 1.9: before that the server sent one attach
 * packet per passenger (the same packet also carries leashes), afterwards it sends the vehicle's
 * complete passenger list in a single mount packet. The entity tracker handles both, so this view
 * covers both and reports which of the two it is wrapping rather than forcing the consumer to
 * branch on engine specific packet type constants.
 * <p>
 * Only the fields the tracker reads are exposed; neither backend writes to these packets.
 */
public interface EntityAttachView {

  Player player();

  /** @return true for the 1.9+ mount packet, which carries the vehicle's full passenger list. */
  boolean isMount();

  /** @return true for the legacy attach packet, which carries a single passenger or a leash. */
  boolean isLegacyAttach();

  /**
   * @return the runtime id of the vehicle. On the legacy attach packet this is -1 when the
   * passenger is being detached rather than mounted.
   */
  int vehicleId();

  /** @return the vehicle's passengers. Mount packet only. */
  int[] passengers();

  /**
   * @return true when the legacy attach packet describes a leash rather than a mount. Leashes are
   * ignored by the tracker: they do not move the leashed entity with the holder.
   */
  boolean isLeash();

  /** @return the runtime id of the attached passenger. Legacy attach packet only. */
  int passengerId();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

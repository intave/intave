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
import org.jetbrains.annotations.Nullable;

/**
 * Engine neutral view of the entity position sync packet.
 * <p>
 * {@code ClientboundEntityPositionSyncPacket} is the 1.21.2 addition that carries an entity's
 * authoritative position outside of the interpolated movement packets. Its payload is a
 * position/delta/rotation structure of which the tracker uses only the position, and unlike the
 * teleport packet of the same generation it carries no relative flags: the position is always
 * absolute, so this view needs neither a flag set nor a version predicate.
 * <p>
 * The packet exists on one protocol generation only, which is also the only one either backend can
 * deliver it on, so there is no older wire shape to normalise.
 */
public interface EntityTrackerPositionSyncView {

  Player player();

  /** @return the addressed entity id, or null when the packet carries none. */
  @Nullable
  Integer entityId();

  /** @return the absolute position the packet synchronises the entity to, in blocks. */
  Position position();

  /** Releases engine resources held for this packet, if any. */
  void release();
}

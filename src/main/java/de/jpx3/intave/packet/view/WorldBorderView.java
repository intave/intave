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

import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.entity.Player;

/**
 * Engine neutral view of the five outbound world border packets Intave tracks: the pre 1.17
 * combined packet and the four modern per action ones (initialize, set center, set size, lerp
 * size).
 * <p>
 * Unlike the other families this one does not expose the individual payload fields. Its single
 * consumer never looks at them: it only folds the packet into the border it already holds, and
 * every field is meaningful for exactly one of the six update types, so a field-per-getter
 * interface would be five sixths null on every packet. The fold itself is therefore the seam, and
 * {@link #updated(WorldBorder)} is a pure function of the packet plus the previous border - which
 * is what lets the PacketEvents implementation decode eagerly, while the packet buffer is still
 * valid, and apply later inside the tick feedback callback.
 */
public interface WorldBorderView {

  Player player();

  /**
   * Applies this packet's update to the given border.
   *
   * @return the resulting border, which is the argument itself when the packet carries no field
   * this check tracks (the two warning packets of the legacy combined packet).
   */
  WorldBorder updated(WorldBorder border);
}

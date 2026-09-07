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

import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Engine neutral view of an inbound block interaction packet (block placement / use item).
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: the placement and interaction checks only
 * read the clicked block, the clicked face, the cursor position and the block acknowledgement
 * sequence, so those are the only operations exposed. The block position is handed out as the
 * engine neutral {@link BlockPosition}; consumers that still need the ProtocolLib wrapper can ask
 * {@link ProtocolLibBlockInteractionView} for it directly.
 */
public interface BlockInteractionView {

  Player player();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  /** @return the clicked block, or null when the packet carries none (use item, or an empty click). */
  @Nullable
  BlockPosition blockPosition();

  /**
   * @return the clicked block face as its vanilla index, or 255 when the packet carries no face.
   * 255 is what the checks treat as "empty interaction", so it is kept rather than translated.
   */
  int enumDirection();

  /** @return the clicked block face, or null when {@link #enumDirection()} is not a real face. */
  @Nullable
  Direction direction();

  /** @return the in-block cursor position, or null when the packet does not carry one. */
  @Nullable
  Vector facingVector();

  /**
   * @return the block acknowledgement sequence the client sent, or, on versions predating it, a
   * simulated one drawn from the user's connection metadata. The simulated value is drawn at most
   * once per packet, so repeated calls on the same view return the same number.
   */
  int sequenceNumber(User user);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

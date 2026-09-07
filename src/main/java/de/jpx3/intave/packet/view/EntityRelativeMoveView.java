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
import org.jetbrains.annotations.Nullable;

/**
 * Engine neutral view of an outbound relative entity move packet - the
 * {@code REL_ENTITY_MOVE}, {@code REL_ENTITY_MOVE_LOOK} and {@code ENTITY_LOOK} family, which share
 * one packet class on the server and differ only in which of its fields the sender filled in.
 *
 * <h2>Why the deltas are exposed in wire units and not in blocks</h2>
 * {@link de.jpx3.intave.module.tracker.entity.Entity} accumulates entity positions in the client's
 * own fixed point units and only divides when it needs a block position, so that the tracked
 * position drifts exactly the way the client's does. Handing out a {@code double} in blocks would
 * force the tracker to re-multiply and would make the tracked position depend on Intave's rounding
 * rather than the client's. The view therefore reports the raw integer delta plus the
 * {@link #divisor()} that turns it into blocks, which is precisely the pair
 * {@code Entity#applyRelativeMove(long, long, long, double)} consumes.
 * <p>
 * The divisor is 4096 from 1.9 on and 32 below it, matching both the wire format and the divisor
 * the ProtocolLib path has always used.
 *
 * <h2>Rotation only packets</h2>
 * {@code ENTITY_LOOK} carries no deltas. On the server its packet class inherits the delta fields
 * from the shared move packet and leaves them at zero, which is what the ProtocolLib backend reads;
 * the PacketEvents backend reports zero for the same reason. A rotation only packet therefore moves
 * the tracked entity by nothing on either engine.
 */
public interface EntityRelativeMoveView {

  Player player();

  /**
   * @return the runtime id of the moved entity, or null when the packet carries no readable entity
   * id. Boxed because the ProtocolLib backend can legitimately answer "absent" here and the tracker
   * has always branched on that.
   */
  @Nullable Integer entityId();

  /** @return the x delta in wire units; divide by {@link #divisor()} for blocks. */
  long deltaX();

  /** @return the y delta in wire units; divide by {@link #divisor()} for blocks. */
  long deltaY();

  /** @return the z delta in wire units; divide by {@link #divisor()} for blocks. */
  long deltaZ();

  /** @return the number of wire units per block: 4096 on 1.9 and above, 32 below it. */
  double divisor();

  /** Releases engine resources held for this packet. */
  void release();
}

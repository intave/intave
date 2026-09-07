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
import de.jpx3.intave.share.Position;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Engine neutral view of the absolute entity teleport packet.
 * <p>
 * The wire shape of this packet changed twice. Up to 1.8 it carries the position as three
 * fixed point integers of 1/32 of a block; from 1.9 as three absolute doubles; from 1.21.2 as a
 * position/delta/rotation structure whose position is a <em>per axis change</em> qualified by a set
 * of relative flags, so an axis marked relative carries an offset from the position the receiver
 * already tracks rather than an absolute coordinate.
 * <p>
 * Both backends normalise that into one shape: {@link #position()} is the payload position exactly
 * as the packet carries it, in blocks, and {@link #relativeFlags()} says which axes of it are
 * offsets. Below 1.21.2 the flag set is empty, and resolving an empty flag set against any tracked
 * position yields the payload unchanged - which is what "absolute" means - so the consumer needs no
 * version branch of its own.
 *
 * <h2>Why the two version predicates are on the view</h2>
 * {@link #resolvesRelatively()} and {@link #toWirePosition(double)} are the two remaining places
 * where the tracker's behaviour depends on the protocol generation, and both are answered by the
 * backend that decoded the packet rather than by Intave's version table. The backend is the only
 * one that knows which of its own branches produced the values it just handed out: PacketEvents
 * switches this packet's format at 1.21.2, one release before Intave's own {@code VER1_21_3}
 * boundary, and classifying with anything but the branch that actually ran would describe values
 * that were never decoded that way.
 */
public interface EntityTrackerTeleportView {

  Player player();

  /** @return the addressed entity id, or null when the packet carries none. */
  @Nullable
  Integer entityId();

  /**
   * @return the position the payload carries, in blocks. Absolute on every axis not named by
   * {@link #relativeFlags()}, an offset from the tracked position on the axes that are.
   */
  Position position();

  /**
   * @return the relative flags of this teleport; empty on every protocol below 1.21.2, which
   * carries no flags at all and is therefore always absolute.
   */
  Set<Relative> relativeFlags();

  /**
   * @return true when this packet's payload is resolved against the position the tracker already
   * holds - the 1.21.2 and newer format. Besides the resolution itself the tracker owes two things
   * to this generation and not to the older ones: it stops maintaining the fixed point position
   * accumulators from the immediate teleport path, and it treats a teleport of more than 64 blocks
   * as an instant reposition instead of an interpolation target.
   */
  boolean resolvesRelatively();

  /**
   * @return the value the entity's fixed point position accumulator holds for a resolved
   * coordinate: 1/4096 of a block from 1.9 on, 1/32 of a block below it - the very unit the packet
   * carried on that protocol.
   */
  long toWirePosition(double coordinate);

  /** Releases engine resources held for this packet, if any. */
  void release();
}

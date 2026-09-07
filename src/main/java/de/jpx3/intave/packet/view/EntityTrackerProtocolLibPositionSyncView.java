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

import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.share.Position;
import de.jpx3.intave.share.PositionMoveRotation;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * {@link EntityTrackerPositionSyncView} backed by ProtocolLib.
 * <p>
 * Reads the same field {@code Entity#immediateEntityPositionSync} and
 * {@code Entity#handleEntityPositionSync} read inline before the view existed: the packet's single
 * {@code PositionMoveRotation} record, converted out of the container by
 * {@link PositionMoveRotation#firstFrom}, of which only the position is used.
 * <p>
 * The position is read lazily and then cached. Lazily, because the tracker drops these packets on
 * the entity id alone; cached, because the delayed half of the sync runs inside a tick feedback
 * callback, long after this listener returned, and re-reading the container there would observe
 * whatever later listeners did to the packet rather than what was decoded when the sync was
 * applied. The immediate half always runs first, so the cache is filled while the packet is still
 * the one this subscription saw.
 */
public final class EntityTrackerProtocolLibPositionSyncView implements EntityTrackerPositionSyncView {

  private final PacketEvent event;

  private Position position;

  public EntityTrackerProtocolLibPositionSyncView(PacketEvent event) {
    this.event = event;
  }

  /** @return the wrapped event, for position sync code that still needs ProtocolLib specifics. */
  public PacketEvent event() {
    return event;
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public @Nullable Integer entityId() {
    return event.getPacket().getIntegers().readSafely(0);
  }

  @Override
  public Position position() {
    if (position == null) {
      position = PositionMoveRotation.firstFrom(event.getPacket()).position();
    }
    return position;
  }

  @Override
  public void release() {
    // Nothing held: this view reads a converter straight off the event's packet and never borrows a
    // pooled reader.
  }
}

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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import de.jpx3.intave.module.linker.packet.pe.reader.PeMoveReader;
import org.bukkit.entity.Player;

/**
 * {@link RotationView} backed by PacketEvents.
 * <p>
 * Reuses {@link PeMoveReader}, the same decoder the movement view sits on, because the look
 * packets are flying packets as far as PacketEvents is concerned. The reader decodes once into
 * primitives and only re-encodes when something was written, so a packet with a legal pitch never
 * pays for the round trip.
 */
public final class PacketEventsRotationView implements RotationView {

  private final PacketReceiveEvent event;
  private final PeMoveReader reader;

  private PacketEventsRotationView(PacketReceiveEvent event, PeMoveReader reader) {
    this.event = event;
    this.reader = reader;
  }

  /** @return a view over the event, or null when the packet carries no rotation. */
  public static PacketEventsRotationView of(PacketReceiveEvent event) {
    PeMoveReader reader = PeMoveReader.of(event);
    if (reader == null || !reader.hasRotation()) {
      return null;
    }
    return new PacketEventsRotationView(event, reader);
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean cancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public float yaw() {
    return reader.yaw();
  }

  @Override
  public float pitch() {
    return reader.pitch();
  }

  @Override
  public void setPitch(float pitch) {
    reader.setPitch(pitch);
  }

  @Override
  public void release() {
    reader.flush();
  }
}

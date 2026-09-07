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
import de.jpx3.intave.packet.reader.EntityUseReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AttackView} backed by ProtocolLib.
 * <p>
 * Two entry points, matching the two shapes the entity use subscriptions have:
 * <ul>
 *   <li>the injected form, where the subscription linker already handed the handler a pooled
 *       {@link EntityUseReader} and a cancellation handle;</li>
 *   <li>the raw {@link PacketEvent} form, where the handler used to call
 *       {@code PacketReaders.readerOf(event.getPacket())} itself. Mirrors
 *       {@link ProtocolLibMovementView}, so the reader pooling and release semantics stay
 *       exactly as they were.</li>
 * </ul>
 */
public final class ProtocolLibAttackView implements AttackView {

  private final Player player;
  private final EntityUseReader reader;
  private final Cancellable cancellable;
  private final @Nullable PacketEvent event;

  public ProtocolLibAttackView(Player player, EntityUseReader reader, Cancellable cancellable) {
    this.player = player;
    this.reader = reader;
    this.cancellable = cancellable;
    this.event = null;
  }

  /**
   * Wraps a raw entity use packet event, acquiring the pooled reader for it. The caller keeps the
   * responsibility of calling {@link #release()} exactly as it did for the bare reader.
   */
  public ProtocolLibAttackView(PacketEvent event) {
    this.player = event.getPlayer();
    this.reader = PacketReaders.readerOf(event.getPacket());
    this.cancellable = event;
    this.event = event;
  }

  /** @return the wrapped reader, for combat code that still needs ProtocolLib specifics. */
  public EntityUseReader reader() {
    return reader;
  }

  /**
   * @return the wrapped packet event, or null when this view was built from an injected reader.
   *         Only for the packet hold path, which has to clone the container for a later resend.
   */
  public @Nullable PacketEvent packetEvent() {
    return event;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public boolean cancelled() {
    return cancellable.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    // A read only event refuses cancellation; the packet hold path cleared the flag by hand
    // before cancelling, so the event backed view keeps doing it here.
    if (cancelled && event != null && event.isReadOnly()) {
      event.setReadOnly(false);
    }
    cancellable.setCancelled(cancelled);
  }

  @Override
  public int entityId() {
    return reader.entityId();
  }

  @Override
  public boolean isAttackPacket() {
    return reader.isAttackPacket();
  }

  @Override
  public boolean isSecondary() {
    return reader.isSecondary();
  }

  @Override
  public void release() {
    reader.release();
  }
}

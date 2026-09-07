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
import de.jpx3.intave.packet.reader.EntityVelocityReader;
import de.jpx3.intave.share.Motion;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * {@link EntityVelocityView} backed by ProtocolLib.
 * <p>
 * Like {@link ProtocolLibAttackView} this wraps the reader and cancellation handle the
 * subscription linker injected rather than a raw packet event, so the pooled reader keeps its
 * existing lifecycle. Not every velocity subscription asks for a {@link Cancellable} or a
 * {@link PacketEvent}; those two are optional and a subscription that did not request them simply
 * behaves as a read only observer, exactly as it does today.
 */
public final class ProtocolLibEntityVelocityView implements EntityVelocityView {

  private final Player player;
  private final EntityVelocityReader reader;
  private final Cancellable cancellable;
  private final PacketEvent event;

  public ProtocolLibEntityVelocityView(Player player, EntityVelocityReader reader) {
    this(player, reader, null, null);
  }

  public ProtocolLibEntityVelocityView(
    Player player,
    EntityVelocityReader reader,
    Cancellable cancellable,
    PacketEvent event
  ) {
    this.player = player;
    this.reader = reader;
    this.cancellable = cancellable;
    this.event = event;
  }

  /** @return the wrapped reader, for velocity code that still needs ProtocolLib specifics. */
  public EntityVelocityReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public int entityId() {
    return reader.entityId();
  }

  @Override
  public double motionX() {
    return reader.motionX();
  }

  @Override
  public double motionY() {
    return reader.motionY();
  }

  @Override
  public double motionZ() {
    return reader.motionZ();
  }

  @Override
  public Motion motion() {
    return reader.motion();
  }

  @Override
  public void setMotionX(double motionX) {
    reader.setMotionX(motionX);
  }

  @Override
  public void setMotionZ(double motionZ) {
    reader.setMotionZ(motionZ);
  }

  @Override
  public void setMotion(Motion motion) {
    reader.setMotion(motion);
  }

  @Override
  public boolean readOnly() {
    // No event injected means the subscription never asked for one and cannot cancel either.
    return event == null || event.isReadOnly();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    if (cancellable != null) {
      cancellable.setCancelled(cancelled);
    }
  }

  @Override
  public void release() {
    // The subscription linker releases the pooled reader once the subscription returns; going
    // through releaseSafe keeps this idempotent so an early release here changes nothing.
    reader.releaseSafe();
  }
}

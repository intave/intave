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

import de.jpx3.intave.packet.reader.PayloadInReader;
import io.netty.buffer.ByteBuf;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * {@link PayloadInView} backed by ProtocolLib.
 * <p>
 * Like {@link ProtocolLibAttackView} this wraps the reader and cancellation handle the subscription
 * linker already injected, so the pooled {@link PayloadInReader} keeps its existing release
 * semantics. Subscriptions that do not take a {@link Cancellable} may use the two argument
 * constructor; cancellation is then a no-op, matching what those handlers can do today.
 */
public final class ProtocolLibPayloadInView implements PayloadInView {

  private final Player player;
  private final PayloadInReader reader;
  private final Cancellable cancellable;

  public ProtocolLibPayloadInView(Player player, PayloadInReader reader) {
    this(player, reader, null);
  }

  public ProtocolLibPayloadInView(Player player, PayloadInReader reader, Cancellable cancellable) {
    this.player = player;
    this.reader = reader;
    this.cancellable = cancellable;
  }

  /** @return the wrapped reader, for payload code that still needs ProtocolLib specifics. */
  public PayloadInReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public boolean cancelled() {
    return cancellable != null && cancellable.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    if (cancellable != null) {
      cancellable.setCancelled(cancelled);
    }
  }

  @Override
  public String tag() {
    return reader.tag();
  }

  @Override
  public ByteBuf readBytes() {
    return reader.readBytes();
  }

  @Override
  public String readStringWithExtraByte() {
    return reader.readStringWithExtraByte();
  }

  @Override
  public void release() {
    reader.release();
  }
}

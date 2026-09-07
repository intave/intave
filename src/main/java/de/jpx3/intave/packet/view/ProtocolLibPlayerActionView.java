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
import de.jpx3.intave.packet.converter.PlayerAction;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.PlayerActionReader;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * {@link PlayerActionView} backed by ProtocolLib.
 * <p>
 * Two shapes of subscription consume this packet today: the movement dispatcher receives its
 * reader and cancellation handle already injected by the subscription linker, while the placement
 * checks receive the raw {@link PacketEvent}. Both are wrapped here so the reader pooling and
 * release semantics stay exactly as they were.
 */
public final class ProtocolLibPlayerActionView implements PlayerActionView {

  private final Player player;
  private final PlayerActionReader reader;
  private final Cancellable cancellable;

  public ProtocolLibPlayerActionView(Player player, PlayerActionReader reader, Cancellable cancellable) {
    this.player = player;
    this.reader = reader;
    this.cancellable = cancellable;
  }

  public ProtocolLibPlayerActionView(PacketEvent event) {
    this(event.getPlayer(), PacketReaders.readerOf(event.getPacket()), event);
  }

  /** @return the wrapped reader, for code that still needs ProtocolLib specifics. */
  public PlayerActionReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public PlayerAction playerAction() {
    return reader.playerAction();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    cancellable.setCancelled(cancelled);
  }

  @Override
  public void release() {
    reader.release();
  }
}

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

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import de.jpx3.intave.annotate.Nullable;
import de.jpx3.intave.packet.reader.BlockInteractionReader;
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.share.BlockPosition;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.util.Vector;

/**
 * {@link BlockInteractionView} backed by ProtocolLib. Wraps the pooled
 * {@link BlockInteractionReader} so the existing reader pooling, sequence number simulation and
 * release semantics stay exactly as they were.
 * <p>
 * Two entry points exist because the placement subscriptions are declared both ways: some receive
 * the raw {@link PacketEvent}, others get the reader and a {@link Cancellable} injected by the
 * subscription linker.
 */
public final class ProtocolLibBlockInteractionView implements BlockInteractionView {

  private final Player player;
  private final PacketContainer packet;
  private final BlockInteractionReader reader;
  private final Cancellable cancellable;

  public ProtocolLibBlockInteractionView(PacketEvent event) {
    this(event.getPlayer(), event.getPacket(), PacketReaders.readerOf(event.getPacket()), event);
  }

  public ProtocolLibBlockInteractionView(
    Player player, PacketContainer packet, BlockInteractionReader reader, Cancellable cancellable
  ) {
    this.player = player;
    this.packet = packet;
    this.reader = reader;
    this.cancellable = cancellable;
  }

  /** @return the wrapped reader, for placement code that still needs ProtocolLib specifics. */
  public BlockInteractionReader reader() {
    return reader;
  }

  /** @return the backing packet, for placement code that still clones or forwards it. */
  public PacketContainer packet() {
    return packet;
  }

  /** @return the clicked block as the ProtocolLib wrapper, for code that has not been converted yet. */
  @Nullable
  public com.comphenix.protocol.wrappers.BlockPosition protocolLibBlockPosition() {
    return reader.blockPosition();
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
    cancellable.setCancelled(cancelled);
  }

  @Override
  public @Nullable BlockPosition blockPosition() {
    return reader.nativeBlockPosition();
  }

  @Override
  public int enumDirection() {
    return reader.enumDirection();
  }

  @Override
  public @Nullable Direction direction() {
    return reader.direction();
  }

  @Override
  public @Nullable Vector facingVector() {
    return reader.facingVector();
  }

  @Override
  public int sequenceNumber(User user) {
    return reader.sequenceNumber(user);
  }

  @Override
  public void release() {
    reader.release();
  }
}

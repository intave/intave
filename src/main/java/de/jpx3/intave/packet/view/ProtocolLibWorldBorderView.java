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
import de.jpx3.intave.packet.reader.PacketReaders;
import de.jpx3.intave.packet.reader.WorldBorderReader;
import de.jpx3.intave.world.border.WorldBorder;
import org.bukkit.entity.Player;

/**
 * {@link WorldBorderView} backed by ProtocolLib.
 * <p>
 * The pooled reader is opened inside {@link #updated(WorldBorder)} rather than at construction,
 * because the consumer folds the border a tick later from inside a feedback callback and that is
 * exactly where the reader used to be borrowed and returned before this family existed. Keeping
 * the borrow window there leaves the pooling behaviour untouched.
 */
public final class ProtocolLibWorldBorderView implements WorldBorderView {

  private final Player player;
  private final PacketContainer packet;

  public ProtocolLibWorldBorderView(Player player, PacketContainer packet) {
    this.player = player;
    this.packet = packet;
  }

  /** @return the wrapped packet. */
  public PacketContainer packet() {
    return packet;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public WorldBorder updated(WorldBorder border) {
    try (WorldBorderReader reader = PacketReaders.readerOf(packet)) {
      return reader.updated(border);
    }
  }
}

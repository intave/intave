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

import de.jpx3.intave.packet.reader.WindowOpenReader;
import org.bukkit.entity.Player;

/**
 * {@link WindowOpenView} backed by ProtocolLib.
 * <p>
 * The subscription receives its reader already injected by the subscription linker, so this view
 * wraps the pooled {@link WindowOpenReader} and keeps its release semantics untouched - both reads
 * are straight delegations, which leaves the legacy path byte for byte what it was, including the
 * reader's own {@code "unknown"} fallbacks.
 */
public final class ProtocolLibWindowOpenView implements WindowOpenView {

  private final Player player;
  private final WindowOpenReader reader;

  public ProtocolLibWindowOpenView(Player player, WindowOpenReader reader) {
    this.player = player;
    this.reader = reader;
  }

  /** @return the wrapped reader, for inventory code that still needs ProtocolLib specifics. */
  public WindowOpenReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public int containerId() {
    return reader.containerId();
  }

  @Override
  public String menuType() {
    return reader.menuType();
  }

  @Override
  public void release() {
    reader.release();
  }
}

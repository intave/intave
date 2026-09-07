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

import de.jpx3.intave.packet.reader.WindowItemReader;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * {@link WindowItemView} backed by ProtocolLib.
 * <p>
 * The inventory subscriptions receive their reader already injected by the subscription linker, so
 * this view wraps the pooled {@link WindowItemReader} and keeps its release semantics untouched.
 */
public final class ProtocolLibWindowItemView implements WindowItemView {

  private final Player player;
  private final WindowItemReader reader;

  public ProtocolLibWindowItemView(Player player, WindowItemReader reader) {
    this.player = player;
    this.reader = reader;
  }

  /** @return the wrapped reader, for inventory code that still needs ProtocolLib specifics. */
  public WindowItemReader reader() {
    return reader;
  }

  @Override
  public Player player() {
    return player;
  }

  @Override
  public int windowId() {
    return reader.windowId();
  }

  @Override
  public Map<Integer, ItemStack> itemMap() {
    return reader.itemMap();
  }

  @Override
  public boolean full() {
    return reader.full();
  }

  @Override
  public Integer revision() {
    return reader.revision();
  }

  @Override
  public boolean carriedItemKnown() {
    return reader.carriedItemKnown();
  }

  @Override
  public ItemStack carriedItem() {
    return reader.carriedItem();
  }

  @Override
  public void release() {
    reader.release();
  }
}

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

import ac.intave.samples.event.InventoryActionEvent;
import de.jpx3.intave.packet.reader.WindowClickReader;
import de.jpx3.intave.packet.reader.WindowClickReader.InventoryClickType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * {@link WindowClickView} backed by ProtocolLib.
 * <p>
 * Like {@link ProtocolLibAttackView}, the window click subscriptions receive their reader and, where
 * they cancel, their cancellation handle already injected by the subscription linker, so this view
 * wraps those rather than a raw packet event. Every call is a straight delegation, keeping the
 * legacy path byte for byte what it was; {@link #clickedItemTypeIfPossible(Player)} and
 * {@link #action()} delegate too, so the reader stays the single source of truth there.
 */
public final class ProtocolLibWindowClickView implements WindowClickView {

  private final Player player;
  private final WindowClickReader reader;
  private final Cancellable cancellable;

  public ProtocolLibWindowClickView(Player player, WindowClickReader reader) {
    this(player, reader, null);
  }

  public ProtocolLibWindowClickView(
    Player player, WindowClickReader reader, Cancellable cancellable
  ) {
    this.player = player;
    this.reader = reader;
    this.cancellable = cancellable;
  }

  /** @return the wrapped reader, for inventory code that still needs ProtocolLib specifics. */
  public WindowClickReader reader() {
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
  public int containerId() {
    return reader.containerId();
  }

  @Override
  public int slot() {
    return reader.slot();
  }

  @Override
  public int button() {
    return reader.button();
  }

  @Override
  public InventoryClickType clickType() {
    return reader.clickType();
  }

  @Override
  public InventoryActionEvent.Action action() {
    return reader.action();
  }

  @Override
  public Integer revision() {
    return reader.revision();
  }

  @Override
  public ItemStack itemStack() {
    return reader.itemStack();
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
  public Map<Integer, ItemStack> predictedSlots() {
    return reader.predictedSlots();
  }

  @Override
  public boolean isDrop() {
    return reader.isDrop();
  }

  @Override
  public boolean missingItemStack() {
    return reader.missingItemStack();
  }

  @Override
  public String clickedItemTypeIfPossible(Player player) {
    return reader.clickedItemTypeIfPossible(player);
  }

  @Override
  public void release() {
    reader.release();
  }
}

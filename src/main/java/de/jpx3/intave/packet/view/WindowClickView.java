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
import de.jpx3.intave.packet.reader.WindowClickReader.InventoryClickType;
import de.jpx3.intave.user.User;
import de.jpx3.intave.user.UserRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Engine neutral view of an inbound container click packet.
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: only the operations the inventory checks and
 * the sample dispatcher actually perform are exposed. The click type stays
 * {@link InventoryClickType} because that enum is plain data shared by both backends, so consumer
 * comparisons keep working unchanged.
 */
public interface WindowClickView {

  Player player();

  boolean cancelled();

  void setCancelled(boolean cancelled);

  /** @return the id of the container the click happened in; 0 is the player's own inventory. */
  int containerId();

  int slot();

  int button();

  InventoryClickType clickType();

  /** Legacy action number or modern container state ID, null when the field is absent. */
  Integer revision();

  /** @return the item carried on the wire, or null when the packet carries none. */
  ItemStack itemStack();

  /** @return true when this protocol version sends the client's predicted cursor stack. */
  boolean carriedItemKnown();

  /** The client's predicted cursor stack on modern container-click packets. */
  ItemStack carriedItem();

  /** The client's predicted changed slots on modern container-click packets. */
  Map<Integer, ItemStack> predictedSlots();

  boolean isDrop();

  /** @return true for click types whose packet does not describe the moved item. */
  boolean missingItemStack();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();

  /**
   * The type name of the item Intave believes sits in the clicked slot, or null when the click was
   * not in the player's own inventory or the slot is unknown.
   * <p>
   * Engine neutral: it only reads the tracked inventory sample, so both backends share it.
   */
  default String clickedItemTypeIfPossible(Player player) {
    if (containerId() == 0 && slot() >= 0) {
      User user = UserRepository.userOf(player);
      List<String> items = user.meta().inventory().items();
      int slot = slot();
      return items == null || slot >= items.size() ? null : items.get(slot);
    } else {
      return null;
    }
  }

  /**
   * Classifies the click into the sample action the inventory samples expose.
   * <p>
   * Kept in sync with {@code WindowClickReader.actionOf}, which is package private to the reader
   * package; the ProtocolLib view overrides this with a direct delegation to that reader so the
   * legacy path cannot drift.
   */
  default InventoryActionEvent.Action action() {
    InventoryClickType clickType = clickType();
    int slot = slot();
    int button = button();
    switch (clickType) {
      case PICKUP:
        if (slot == -999) {
          return button == 0
            ? InventoryActionEvent.Action.DROP_CURSOR_STACK
            : button == 1
              ? InventoryActionEvent.Action.DROP_CURSOR_ONE
              : InventoryActionEvent.Action.UNKNOWN;
        }
        return button == 0
          ? InventoryActionEvent.Action.CLICK_PRIMARY
          : button == 1
            ? InventoryActionEvent.Action.CLICK_SECONDARY
            : InventoryActionEvent.Action.UNKNOWN;
      case QUICK_MOVE:
        return InventoryActionEvent.Action.QUICK_MOVE;
      case SWAP:
        return button >= 0 && button <= 8
          ? InventoryActionEvent.Action.SWAP_HOTBAR
          : button == 40
            ? InventoryActionEvent.Action.SWAP_OFFHAND
            : InventoryActionEvent.Action.UNKNOWN;
      case CLONE:
        return InventoryActionEvent.Action.CLONE;
      case THROW:
        return button == 0
          ? InventoryActionEvent.Action.DROP_SLOT_ONE
          : button == 1
            ? InventoryActionEvent.Action.DROP_SLOT_STACK
            : InventoryActionEvent.Action.UNKNOWN;
      case QUICK_CRAFT:
        switch (button & 3) {
          case 0:
            return InventoryActionEvent.Action.DRAG_START;
          case 1:
            return InventoryActionEvent.Action.DRAG_ADD_SLOT;
          case 2:
            return InventoryActionEvent.Action.DRAG_END;
          default:
            return InventoryActionEvent.Action.UNKNOWN;
        }
      case PICKUP_ALL:
        return InventoryActionEvent.Action.COLLECT_TO_CURSOR;
      default:
        return InventoryActionEvent.Action.UNKNOWN;
    }
  }
}

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

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Engine neutral view of an outbound entity equipment packet, with write access to the items.
 * <p>
 * The equipment filter only ever rewrites the item of every slot the packet carries and never
 * looks at the slot itself or at the entity, so the view exposes the items as a flat indexed list
 * and nothing else. The two wire layouts - the single item of 1.8 to 1.15 and the slot/item pair
 * list of 1.16+ - are both normalised into that one list by the backends, exactly as the filter's
 * own version branch did before the view existed.
 * <p>
 * The list is a snapshot: writes go through {@link #setItem(int, ItemStack)} rather than through
 * the returned list, which keeps the flush strategy the backend's business.
 */
public interface EntityEquipmentView {

  Player player();

  /**
   * @return the equipped item of every slot this packet updates, in wire order. An entry is null
   * only when the packet carries no item for that slot at all, which callers should skip.
   */
  List<ItemStack> items();

  /** Replaces the item at the given index of {@link #items()}. */
  void setItem(int index, ItemStack itemStack);

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

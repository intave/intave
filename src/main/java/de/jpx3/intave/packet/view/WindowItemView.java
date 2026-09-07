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

import java.util.Map;

/**
 * Engine neutral view of an outbound window content packet (window items / set slot).
 * <p>
 * Mirrors {@link MovementView} and {@link AttackView}: the inventory trackers only need the
 * container id, the slot contents and the 1.17+ state id plus carried item, so those are the only
 * operations exposed. Both backends normalise the bulk ("window items") and the single slot
 * ("set slot") packet into the same shape, exactly as the ProtocolLib readers already did.
 */
public interface WindowItemView {

  Player player();

  /** @return the container id the update targets; -1 is the client's own cursor / carried slot. */
  int windowId();

  /** @return slot index to item for every slot this packet updates. */
  Map<Integer, ItemStack> itemMap();

  /** @return true when the packet replaces the whole container rather than a single slot. */
  boolean full();

  /** @return the 1.17+ container state id, or null on older protocols that do not carry one. */
  Integer revision();

  /** @return true when this packet tells us what the player carries on the cursor. */
  boolean carriedItemKnown();

  /** @return the carried (cursor) item, or null when {@link #carriedItemKnown()} is false. */
  ItemStack carriedItem();

  /** Writes back any modification and releases engine resources held for this packet. */
  void release();
}

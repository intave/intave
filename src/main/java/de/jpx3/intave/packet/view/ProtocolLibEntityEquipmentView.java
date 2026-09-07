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
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link EntityEquipmentView} backed by ProtocolLib.
 * <p>
 * Carries the layout probe the equipment filter used before the view existed, unchanged: 1.8 to
 * 1.15 put a single item on the packet, so a non null item modifier at index 0 means the legacy
 * layout, and its absence means the 1.16+ slot/item pair list. The probe is the version check -
 * the filter never asked {@code MinecraftVersions} - so it stays a probe here.
 * <p>
 * On the legacy layout the item is handed out as read and the rewrite goes back through the item
 * modifier. On the pair layout each item is handed out as a clone and the rewrite is a
 * {@code setSecond} on the live pair, which is what the filter did; a ProtocolLib packet is
 * mutated in place, so no flush is needed in {@link #release()}.
 */
public final class ProtocolLibEntityEquipmentView implements EntityEquipmentView {

  private final PacketEvent event;
  private final PacketContainer packet;
  private final List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairs;
  private final List<ItemStack> items;

  public ProtocolLibEntityEquipmentView(PacketEvent event) {
    this.event = event;
    this.packet = event.getPacket();
    ItemStack legacyItem = packet.getItemModifier().readSafely(0);
    if (legacyItem != null) {
      // 1.8 - 1.15
      this.pairs = null;
      this.items = Collections.singletonList(legacyItem);
    } else {
      this.pairs = packet.getSlotStackPairLists().read(0);
      List<ItemStack> items = new ArrayList<>(pairs.size());
      for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : pairs) {
        ItemStack item = pair.getSecond();
        items.add(item == null ? null : item.clone());
      }
      this.items = items;
    }
  }

  @Override
  public Player player() {
    return event.getPlayer();
  }

  @Override
  public List<ItemStack> items() {
    return items;
  }

  @Override
  public void setItem(int index, ItemStack itemStack) {
    if (pairs == null) {
      packet.getItemModifier().write(index, itemStack);
    } else {
      pairs.get(index).setSecond(itemStack);
    }
  }

  @Override
  public void release() {
    // Nothing held: ProtocolLib packets are mutated in place, so the writes are already visible.
  }
}

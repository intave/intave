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

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import de.jpx3.intave.adapter.MinecraftVersions;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link WindowItemView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction: every PacketEvents getter re-reads the packet
 * buffer, and the inventory path asks for the slot map and the carried item separately. The two
 * packet layouts are folded into the same fields here the way {@code WindowBulkItemReader} and
 * {@code WindowSingleItemReader} do on the ProtocolLib side, including the 1.17 state id gate.
 */
public final class PacketEventsWindowItemView implements WindowItemView {

  private final PacketSendEvent event;
  private final int windowId;
  private final Map<Integer, ItemStack> itemMap;
  private final boolean full;
  private final Integer revision;
  private final boolean carriedItemKnown;
  private final ItemStack carriedItem;

  private PacketEventsWindowItemView(
    PacketSendEvent event,
    int windowId,
    Map<Integer, ItemStack> itemMap,
    boolean full,
    Integer revision,
    boolean carriedItemKnown,
    ItemStack carriedItem
  ) {
    this.event = event;
    this.windowId = windowId;
    this.itemMap = itemMap;
    this.full = full;
    this.revision = revision;
    this.carriedItemKnown = carriedItemKnown;
    this.carriedItem = carriedItem;
  }

  /** @return a view over the event, or null when the packet is not a window content update. */
  public static PacketEventsWindowItemView of(PacketSendEvent event) {
    if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
      return ofWindowItems(event);
    }
    if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
      return ofSetSlot(event);
    }
    return null;
  }

  private static PacketEventsWindowItemView ofWindowItems(PacketSendEvent event) {
    WrapperPlayServerWindowItems wrapper = new WrapperPlayServerWindowItems(event);
    List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = wrapper.getItems();
    Map<Integer, ItemStack> map = new HashMap<>();
    if (items != null) {
      for (int i = 0; i < items.size(); i++) {
        map.put(i, toBukkit(items.get(i)));
      }
    }
    // Only 1.17+ carries a state id and a cursor item on this packet.
    boolean modern = MinecraftVersions.VER1_17_0.atOrAbove();
    return new PacketEventsWindowItemView(
      event,
      wrapper.getWindowId(),
      map,
      true,
      modern ? wrapper.getStateId() : null,
      modern,
      modern ? toBukkit(wrapper.getCarriedItem().orElse(null)) : null
    );
  }

  private static PacketEventsWindowItemView ofSetSlot(PacketSendEvent event) {
    WrapperPlayServerSetSlot wrapper = new WrapperPlayServerSetSlot(event);
    int slot = wrapper.getSlot();
    ItemStack item = toBukkit(wrapper.getItem());
    Map<Integer, ItemStack> map = new HashMap<>();
    if (slot != -1) {
      map.put(slot, item);
    }
    boolean carriedItemKnown = slot == -1;
    return new PacketEventsWindowItemView(
      event,
      wrapper.getWindowId(),
      map,
      false,
      MinecraftVersions.VER1_17_0.atOrAbove() ? wrapper.getStateId() : null,
      carriedItemKnown,
      carriedItemKnown ? item : null
    );
  }

  /**
   * PacketEvents models an empty slot as its own empty stack while ProtocolLib hands out a Bukkit
   * air stack; normalise to air so consumers can call {@code getType()} without a null check.
   */
  private static ItemStack toBukkit(com.github.retrooper.packetevents.protocol.item.ItemStack item) {
    if (item == null || item.isEmpty()) {
      return new ItemStack(Material.AIR);
    }
    ItemStack converted = SpigotConversionUtil.toBukkitItemStack(item);
    return converted == null ? new ItemStack(Material.AIR) : converted;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public int windowId() {
    return windowId;
  }

  @Override
  public Map<Integer, ItemStack> itemMap() {
    return itemMap;
  }

  @Override
  public boolean full() {
    return full;
  }

  @Override
  public Integer revision() {
    return revision;
  }

  @Override
  public boolean carriedItemKnown() {
    return carriedItemKnown;
  }

  @Override
  public ItemStack carriedItem() {
    return carriedItem;
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into Bukkit values at construction.
  }
}

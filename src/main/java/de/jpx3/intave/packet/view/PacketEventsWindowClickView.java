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

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import de.jpx3.intave.adapter.MinecraftVersions;
import de.jpx3.intave.packet.reader.WindowClickReader.InventoryClickType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link WindowClickView} backed by PacketEvents.
 * <p>
 * The wrapper is decoded once at construction because the inventory checks query the same fields
 * several times per packet and every PacketEvents getter re-reads the packet buffer. PacketEvents
 * already normalises the legacy layouts (short slot, byte button, byte click mode) into the modern
 * fields, so the version branching the ProtocolLib reader needs is not repeated here; the two
 * version gates that remain, {@link #carriedItemKnown()} and {@link #predictedSlots()}, are the
 * server side ones the samples themselves depend on.
 */
public final class PacketEventsWindowClickView implements WindowClickView {

  private final PacketReceiveEvent event;
  private final int containerId;
  private final int slot;
  private final int button;
  private final InventoryClickType clickType;
  private final Integer revision;
  private final ItemStack carriedItem;
  private final Map<Integer, ItemStack> predictedSlots;

  private PacketEventsWindowClickView(
    PacketReceiveEvent event,
    int containerId,
    int slot,
    int button,
    InventoryClickType clickType,
    Integer revision,
    ItemStack carriedItem,
    Map<Integer, ItemStack> predictedSlots
  ) {
    this.event = event;
    this.containerId = containerId;
    this.slot = slot;
    this.button = button;
    this.clickType = clickType;
    this.revision = revision;
    this.carriedItem = carriedItem;
    this.predictedSlots = predictedSlots;
  }

  /** @return a view over the event, or null when the packet is not a container click. */
  public static PacketEventsWindowClickView of(PacketReceiveEvent event) {
    if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) {
      return null;
    }
    WrapperPlayClientClickWindow wrapper = new WrapperPlayClientClickWindow(event);
    return new PacketEventsWindowClickView(
      event,
      wrapper.getWindowId(),
      wrapper.getSlot(),
      wrapper.getButton(),
      clickTypeOf(wrapper.getWindowClickType()),
      revisionOf(wrapper),
      bukkitItem(wrapper.getCarriedItemStack()),
      predictedSlotsOf(wrapper)
    );
  }

  /**
   * The modern container state id when the client sends one, otherwise the legacy action number.
   * Matches the ProtocolLib reader, which reads the state id on 1.17+ and the action number below.
   */
  private static Integer revisionOf(WrapperPlayClientClickWindow wrapper) {
    Optional<Integer> stateId = wrapper.getStateId();
    if (stateId.isPresent()) {
      return stateId.get();
    }
    return wrapper.getActionNumber().orElse(null);
  }

  private static Map<Integer, ItemStack> predictedSlotsOf(WrapperPlayClientClickWindow wrapper) {
    if (!MinecraftVersions.VER1_17_0.atOrAbove()) {
      return Collections.emptyMap();
    }
    Optional<Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> slots =
      wrapper.getSlots();
    if (!slots.isPresent()) {
      return Collections.emptyMap();
    }
    Map<Integer, ItemStack> converted = new LinkedHashMap<>();
    for (Map.Entry<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> entry
      : slots.get().entrySet()) {
      if (entry.getKey() == null) {
        continue;
      }
      converted.put(entry.getKey(), bukkitItem(entry.getValue()));
    }
    return converted;
  }

  /** @return the Bukkit stack, or null for an absent or empty one, as the ProtocolLib path yields. */
  private static ItemStack bukkitItem(
    com.github.retrooper.packetevents.protocol.item.ItemStack item
  ) {
    if (item == null || item.isEmpty()) {
      return null;
    }
    return SpigotConversionUtil.toBukkitItemStack(item);
  }

  private static InventoryClickType clickTypeOf(
    WrapperPlayClientClickWindow.WindowClickType type
  ) {
    if (type == null) {
      return InventoryClickType.PICKUP;
    }
    switch (type) {
      case QUICK_MOVE:
        return InventoryClickType.QUICK_MOVE;
      case SWAP:
        return InventoryClickType.SWAP;
      case CLONE:
        return InventoryClickType.CLONE;
      case THROW:
        return InventoryClickType.THROW;
      case QUICK_CRAFT:
        return InventoryClickType.QUICK_CRAFT;
      case PICKUP_ALL:
        return InventoryClickType.PICKUP_ALL;
      case PICKUP:
      default:
        return InventoryClickType.PICKUP;
    }
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public boolean cancelled() {
    return event.isCancelled();
  }

  @Override
  public void setCancelled(boolean cancelled) {
    event.setCancelled(cancelled);
  }

  @Override
  public int containerId() {
    return containerId;
  }

  @Override
  public int slot() {
    return slot;
  }

  @Override
  public int button() {
    return button;
  }

  @Override
  public InventoryClickType clickType() {
    return clickType;
  }

  @Override
  public Integer revision() {
    return revision;
  }

  @Override
  public ItemStack itemStack() {
    return carriedItem;
  }

  @Override
  public boolean carriedItemKnown() {
    return MinecraftVersions.VER1_17_0.atOrAbove();
  }

  @Override
  public ItemStack carriedItem() {
    return carriedItemKnown() ? carriedItem : null;
  }

  @Override
  public Map<Integer, ItemStack> predictedSlots() {
    return predictedSlots;
  }

  @Override
  public boolean isDrop() {
    return clickType == InventoryClickType.THROW && slot != -999;
  }

  @Override
  public boolean missingItemStack() {
    switch (clickType) {
      case QUICK_MOVE:
      case SWAP:
        return true;
      default:
        return false;
    }
  }

  @Override
  public void release() {
    // Nothing held: the wrapper was decoded into primitives at construction.
  }
}

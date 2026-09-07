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
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link EntityEquipmentView} backed by PacketEvents.
 * <p>
 * PacketEvents decodes both wire layouts into the same list of {@code Equipment} entries - the
 * pre 1.16 packet simply yields a list of one - so the version branch the ProtocolLib view carries
 * collapses into a straight walk over that list here.
 * <p>
 * The wrapper is decoded once at construction because every PacketEvents getter re-reads the
 * packet buffer, and the re-encode is deferred to {@link #release()} and only done when an item
 * actually changed, so a packet the filter left alone is passed through untouched.
 * <p>
 * Empty slots are normalised in both directions: PacketEvents models them as its own empty stack
 * while the filter works on Bukkit items, so they are handed out as air and written back as
 * {@code ItemStack.EMPTY} rather than as an air stack, which keeps the bytes on the wire identical
 * to what an untouched empty slot would have produced.
 */
public final class PacketEventsEntityEquipmentView implements EntityEquipmentView {

  private final PacketSendEvent event;
  private final WrapperPlayServerEntityEquipment wrapper;
  private final List<Equipment> equipment;
  private final List<ItemStack> items;

  private boolean dirty;

  private PacketEventsEntityEquipmentView(
    PacketSendEvent event,
    WrapperPlayServerEntityEquipment wrapper,
    List<Equipment> equipment,
    List<ItemStack> items
  ) {
    this.event = event;
    this.wrapper = wrapper;
    this.equipment = equipment;
    this.items = items;
  }

  /** @return a view over the event, or null when the packet is not an entity equipment update. */
  public static PacketEventsEntityEquipmentView of(PacketSendEvent event) {
    if (event.getPacketType() != PacketType.Play.Server.ENTITY_EQUIPMENT) {
      return null;
    }
    WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
    List<Equipment> equipment = wrapper.getEquipment();
    if (equipment == null) {
      equipment = Collections.emptyList();
    }
    List<ItemStack> items = new ArrayList<>(equipment.size());
    for (Equipment entry : equipment) {
      items.add(entry == null ? null : toBukkit(entry.getItem()));
    }
    return new PacketEventsEntityEquipmentView(event, wrapper, equipment, items);
  }

  private static ItemStack toBukkit(
    com.github.retrooper.packetevents.protocol.item.ItemStack item
  ) {
    if (item == null || item.isEmpty()) {
      return new ItemStack(Material.AIR);
    }
    ItemStack converted = SpigotConversionUtil.toBukkitItemStack(item);
    return converted == null ? new ItemStack(Material.AIR) : converted;
  }

  private static com.github.retrooper.packetevents.protocol.item.ItemStack fromBukkit(
    ItemStack item
  ) {
    if (item == null || item.getType() == Material.AIR) {
      return com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
    }
    com.github.retrooper.packetevents.protocol.item.ItemStack converted =
      SpigotConversionUtil.fromBukkitItemStack(item);
    return converted == null
      ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
      : converted;
  }

  @Override
  public Player player() {
    Object player = event.getPlayer();
    return player instanceof Player ? (Player) player : null;
  }

  @Override
  public List<ItemStack> items() {
    return items;
  }

  @Override
  public void setItem(int index, ItemStack itemStack) {
    Equipment entry = equipment.get(index);
    if (entry == null) {
      return;
    }
    entry.setItem(fromBukkit(itemStack));
    items.set(index, itemStack);
    dirty = true;
  }

  @Override
  public void release() {
    if (!dirty) {
      return;
    }
    wrapper.setEquipment(equipment);
    event.markForReEncode(true);
    dirty = false;
  }
}

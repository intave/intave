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

package de.jpx3.intave.packet.reader;

import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class WindowSingleItemReader extends AbstractPacketReader implements WindowItemReader {
  @Override
  public int windowId() {
    return packet().getIntegers().read(0);
  }

  public ItemStack item() {
    return packet().getItemModifier().read(0);
  }

  public void setItem(ItemStack item) {
    packet().getItemModifier().write(0, item);
  }

  @Override
  public Map<Integer, ItemStack> itemMap() {
    ItemStack item = item();
    int slot = slot();
    Map<Integer, ItemStack> map = new HashMap<>();
    if (slot != -1) {
      map.put(slot, item);
    }
    return map;
  }

  private int slot() {
    int index = MinecraftVersions.VER1_17_0.atOrAbove() ? 2 : 1;
    Integer integer = packet().getIntegers().readSafely(index);
    return integer == null ? packet().getShorts().readSafely(0) : integer;
  }

  @Override
  public boolean full() {
    return false;
  }

  @Override
  public Integer revision() {
    return MinecraftVersions.VER1_17_0.atOrAbove()
      ? packet().getIntegers().readSafely(1)
      : null;
  }

  @Override
  public boolean carriedItemKnown() {
    return slot() == -1;
  }

  @Override
  public ItemStack carriedItem() {
    return carriedItemKnown() ? packet().getItemModifier().readSafely(0) : null;
  }
}

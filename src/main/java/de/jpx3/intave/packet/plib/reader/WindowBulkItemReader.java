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

package de.jpx3.intave.packet.plib.reader;

import com.google.common.collect.Maps;
import de.jpx3.intave.adapter.MinecraftVersions;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class WindowBulkItemReader extends AbstractPacketReader implements WindowItemReader {
  @Override
  public int windowId() {
    return packet().getIntegers().read(0);
  }

  @Override
  public Map<Integer, ItemStack> itemMap() {
    List<ItemStack> read = packet().getItemListModifier().readSafely(0);
    if (read == null) {
      return Maps.newHashMap();
    }
    // to map with indices
    Map<Integer, ItemStack> map = new java.util.HashMap<>();
    for (int i = 0; i < read.size(); i++) {
      map.put(i, read.get(i));
    }
    return map;
  }

  @Override
  public boolean full() {
    return true;
  }

  @Override
  public Integer revision() {
    return MinecraftVersions.VER1_17_0.atOrAbove()
      ? packet().getIntegers().readSafely(1)
      : null;
  }

  @Override
  public boolean carriedItemKnown() {
    return MinecraftVersions.VER1_17_0.atOrAbove();
  }

  @Override
  public ItemStack carriedItem() {
    return carriedItemKnown() ? packet().getItemModifier().readSafely(0) : null;
  }
}

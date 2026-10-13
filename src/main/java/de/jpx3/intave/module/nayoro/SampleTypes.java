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

package de.jpx3.intave.module.nayoro;

import de.jpx3.intave.share.PlayerHand;

import ac.intave.samples.share.*;
import de.jpx3.intave.entity.size.HitboxSize;
import de.jpx3.intave.share.BlockState;
import de.jpx3.intave.share.BoundingBox;
import de.jpx3.intave.share.Direction;
import de.jpx3.intave.share.RawVector3d;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Converts Intave runtime values at the boundary of the samples API. */
public final class SampleTypes {
  private SampleTypes() {
  }

  public static ac.intave.samples.share.Position position(
    de.jpx3.intave.share.Position position
  ) {
    if (position == null) {
      return null;
    }
    return new ac.intave.samples.share.Position(
      position.getX(), position.getY(), position.getZ()
    );
  }

  public static de.jpx3.intave.share.Position position(
    ac.intave.samples.share.Position position
  ) {
    if (position == null) {
      return null;
    }
    return new de.jpx3.intave.share.Position(position.x(), position.y(), position.z());
  }

  public static ac.intave.samples.share.Rotation rotation(
    de.jpx3.intave.share.Rotation rotation
  ) {
    if (rotation == null) {
      return null;
    }
    return new ac.intave.samples.share.Rotation(rotation.yaw(), rotation.pitch());
  }

  public static de.jpx3.intave.share.Rotation rotation(
    ac.intave.samples.share.Rotation rotation
  ) {
    if (rotation == null) {
      return null;
    }
    return new de.jpx3.intave.share.Rotation(rotation.yaw(), rotation.pitch());
  }

  public static ac.intave.samples.share.HitboxSize hitboxSize(HitboxSize size) {
    return new ac.intave.samples.share.HitboxSize(size.width(), size.height());
  }

  public static ac.intave.samples.share.Direction direction(Direction direction) {
    return direction == null
      ? null
      : ac.intave.samples.share.Direction.valueOf(direction.name());
  }

  public static Hand hand(PlayerHand hand) {
    return hand == null ? null : Hand.valueOf(hand.name());
  }

  public static ac.intave.samples.share.Vector3d vector(RawVector3d vector) {
    return vector == null
      ? null
      : new ac.intave.samples.share.Vector3d(vector.x(), vector.y(), vector.z());
  }

  public static Item item(ItemStack itemStack) {
    Inventory.Item item = Inventory.Item.fromItem(itemStack);
    return new Item(
      item.type(), item.amount(), ItemCategory.valueOf(item.category().name()),
      item.glowing(), item.baseQuality(), item.enchantmentQuality()
    );
  }

  /** Converts an empty Bukkit stack to the samples API's null-as-empty representation. */
  public static Item nullableItem(ItemStack itemStack) {
    if (itemStack == null || itemStack.getType() == org.bukkit.Material.AIR || itemStack.getAmount() <= 0) {
      return null;
    }
    return item(itemStack);
  }

  public static List<SlotUpdate> slotUpdates(Map<Integer, ItemStack> itemStacks) {
    List<SlotUpdate> updates = new ArrayList<>(itemStacks.size());
    itemStacks.entrySet().stream()
      .sorted(Comparator.comparingInt(Map.Entry::getKey))
      .forEach(entry -> updates.add(new SlotUpdate(entry.getKey(), nullableItem(entry.getValue()))));
    return updates;
  }

  public static Item[] items(ItemStack[] itemStacks) {
    if (itemStacks == null) {
      return null;
    }
    Item[] items = new Item[itemStacks.length];
    for (int index = 0; index < itemStacks.length; index++) {
      items[index] = item(itemStacks[index]);
    }
    return items;
  }

  public static Block block(BlockState state, int blockX, int blockY, int blockZ) {
    ArrayList<ac.intave.samples.share.BoundingBox> boxes = new ArrayList<>();
    for (BoundingBox box : state.collisionShape()
      .normalized(blockX, blockY, blockZ)
      .elementaryBoxes()) {
      boxes.add(new ac.intave.samples.share.BoundingBox(
        box.minX, box.minY, box.minZ,
        box.maxX, box.maxY, box.maxZ
      ));
    }
    return new Block(state.type().name(), stringProperties(state.properties()), boxes);
  }

  private static Map<String, String> stringProperties(
    Map<String, ? extends Comparable<?>> properties
  ) {
    SortedMap<String, String> converted = new TreeMap<>();
    for (Map.Entry<String, ? extends Comparable<?>> property : properties.entrySet()) {
      converted.put(property.getKey(), String.valueOf(property.getValue()));
    }
    return converted;
  }
}

package de.jpx3.intave.module.nayoro.event;

import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.Inventory;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import org.bukkit.inventory.ItemStack;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class WindowItemsEvent extends Event {
  private int windowId;
  private int count;

  private final Map<Integer, Inventory.Item> items = new HashMap<>();

  public WindowItemsEvent() {
  }

  public WindowItemsEvent(
    int windowId, int count,
    Map<Integer, ItemStack> items
  ) {
    this.windowId = windowId;
    this.count = count;
    for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
      int slot = entry.getKey();
      this.items.put(slot, Inventory.Item.fromItem(entry.getValue()));
    }
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(windowId);
    out.writeInt(count);
    out.writeInt(items.size());
    for (Map.Entry<Integer, Inventory.Item> entry : items.entrySet()) {
      out.writeInt(entry.getKey());
      entry.getValue().serialize(out);
    }
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    windowId = in.readInt();
    count = in.readInt();
    if (count > 1024) {
      throw new IOException("Too many items: " + count);
    }
    items.clear();
    int size = in.readInt();
    if (size > 1024) {
      throw new IOException("Too many items: " + size);
    }
    for (int i = 0; i < size; i++) {
      int slot = in.readInt();
      items.put(slot, Inventory.Item.deserialize(in));
    }
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public int windowId() {
    return windowId;
  }

  public int count() {
    return count;
  }

  public Map<Integer, Inventory.Item> items() {
    return items;
  }

  public void addItem(int slot, Inventory.Item item) {
    items.put(slot, item);
  }

  public void removeItem(int index) {
    items.remove(index);
  }

  public void clearItems() {
    items.clear();
  }

  public static WindowItemsEvent create(
    int windowId, int slots, Map<Integer, ItemStack> items
  ) {
    return new WindowItemsEvent(windowId, slots, items);
  }
}

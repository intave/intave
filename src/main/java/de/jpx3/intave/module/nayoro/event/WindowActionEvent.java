package de.jpx3.intave.module.nayoro.event;

import de.jpx3.intave.annotate.KeepEnumInternalNames;
import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.Inventory;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;
import org.bukkit.inventory.ItemStack;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class WindowActionEvent extends Event {
  private int windowId;
  private Action action;
  private Inventory.Item[] armorContents;

  private WindowActionEvent(Action action, ItemStack[] armorContents) {
    this.action = action;
    if (armorContents == null) {
      armorContents = new ItemStack[4];
    }
    if (armorContents.length != 4) {
      throw new IllegalArgumentException("armorContents.length != 4");
    }
    this.armorContents = new Inventory.Item[armorContents.length];
    for (int i = 0; i < armorContents.length; i++) {
      this.armorContents[i] = Inventory.Item.fromItem(armorContents[i]);
    }
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(windowId);
    out.writeUTF(action.name());
    out.writeInt(armorContents.length);
    for (Inventory.Item item : armorContents) {
      if (item == null) {
        out.writeBoolean(false);
      } else {
        out.writeBoolean(true);
        item.serialize(out);
      }
    }
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    windowId = in.readInt();
    action = Action.valueOf(in.readUTF());
    int length = in.readInt();
    armorContents = new Inventory.Item[length];
    for (int i = 0; i < length; i++) {
      if (in.readBoolean()) {
        armorContents[i] = Inventory.Item.deserialize(in);
      }
    }
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public static WindowActionEvent create(
    Action action, ItemStack[] armorContents
  ) {
    return new WindowActionEvent(action, armorContents);
  }

  @KeepEnumInternalNames
  public enum Action {
    OPEN,
    INFER_OPEN,
    CLOSE
  }
}

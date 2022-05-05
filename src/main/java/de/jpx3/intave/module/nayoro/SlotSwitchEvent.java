package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class SlotSwitchEvent extends Event {
  private int slot;
  private String material;
  private int amount;

  public SlotSwitchEvent() {
  }

  public SlotSwitchEvent(int slot, String material, int amount) {
    this.slot = slot;
    this.material = material;
    this.amount = amount;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(slot);
    out.writeUTF(material);
    out.writeInt(amount);
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    slot = in.readInt();
    material = in.readUTF();
    amount = in.readInt();
  }

  @Override
  public void accept(EventSink sink) {
    sink.on(this);
  }
}

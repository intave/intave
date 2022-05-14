package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class AttackEvent extends Event {
  private int source;
  private int target;

  public AttackEvent() {
  }

  public AttackEvent(int source, int target) {
    this.source = source;
    this.target = target;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(source);
    out.writeInt(target);
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    source = in.readInt();
    target = in.readInt();
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public static AttackEvent create(int source, int target) {
    return new AttackEvent(source, target);
  }
}

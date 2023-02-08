package de.jpx3.intave.module.nayoro.event;

import de.jpx3.intave.module.nayoro.Environment;
import de.jpx3.intave.module.nayoro.event.sink.EventSink;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class EntityRemoveEvent extends Event {
  private int id;

  public EntityRemoveEvent() {
  }

  public EntityRemoveEvent(int entityId) {
    this.id = entityId;
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(id);
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    id = in.readInt();
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }

  public int id() {
    return id;
  }
}

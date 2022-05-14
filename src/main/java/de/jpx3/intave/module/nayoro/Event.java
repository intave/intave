package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class Event {
  private long offset;
  public abstract void serialize(Environment environment, DataOutput out) throws IOException;
  public abstract void deserialize(Environment environment, DataInput in) throws IOException;
  public abstract void accept(EventSink sink);

  public long offset() {
    return offset;
  }

  public void withOffset(long offset) {
    this.offset = offset;
  }
}

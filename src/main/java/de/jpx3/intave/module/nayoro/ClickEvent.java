package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;

public final class ClickEvent extends Event {
  private final static ClickEvent SINGLETON = new ClickEvent();

  public ClickEvent() {
  }

  @Override
  public void serialize(Environment environment, DataOutput out) {
  }

  @Override
  public void deserialize(Environment environment, DataInput in) {
  }

  @Override
  public void accept(EventSink sink) {
    sink.on(this);
  }

  public static ClickEvent create() {
    return SINGLETON;
  }
}

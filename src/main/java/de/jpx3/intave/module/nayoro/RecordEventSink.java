package de.jpx3.intave.module.nayoro;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;

public class RecordEventSink extends EventSink {
  private final long start = System.currentTimeMillis();
  private final Environment environment;
  private final DataOutput dataOutput;

  public RecordEventSink(Environment environment, DataOutput dataOutput) {
    this.environment = environment;
    this.dataOutput = dataOutput;
  }

  @Override
  public void onAny(Event event) {
    try {
      dataOutput.writeLong(System.currentTimeMillis() - start);
      dataOutput.writeByte(EventRegistry.idOf(event));
      event.serialize(environment, dataOutput);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not serialize event " + event.getClass().getName(), exception);
    }
  }

  @Override
  public void close() {
    try {
      if (dataOutput instanceof Closeable) {
        ((Closeable)dataOutput).close();
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not close data output", exception);
    }
  }
}

package de.jpx3.intave.module.nayoro;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class PropertiesEvent extends Event {
  private final Map<String, Boolean> properties = new HashMap<>();

  public PropertiesEvent() {
  }

  public PropertiesEvent(Map<String, Boolean> properties) {
    this.properties.putAll(properties);
  }

  @Override
  public void serialize(Environment environment, DataOutput out) throws IOException {
    out.writeInt(properties.size());
    for (Map.Entry<String, Boolean> entry : properties.entrySet()) {
      out.writeUTF(entry.getKey());
      out.writeBoolean(entry.getValue());
    }
  }

  @Override
  public void deserialize(Environment environment, DataInput in) throws IOException {
    int size = in.readInt();
    for (int i = 0; i < size; i++) {
      properties.put(in.readUTF(), in.readBoolean());
    }
  }

  public Map<String, Boolean> properties() {
    return properties;
  }

  @Override
  public void accept(EventSink sink) {
    sink.visit(this);
  }
}

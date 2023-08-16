package de.jpx3.intave.module.cloud.protocol;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.annotate.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

public class Identity implements JsonSerializable {
  @Nullable
  private UUID id;
  @Nullable
  private String name;

  private Identity() {
  }

  public Identity(String name) {
    this.name = name;
  }

  public Identity(UUID id) {
    this.id = id;
  }

  public Identity(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      if (id != null) {
        writer.name("uuid").value(id.toString());
      }
      if (name != null) {
        writer.name("name").value(name);
      }
      writer.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(JsonReader reader) {
    try {
      reader.beginObject();
      while (reader.hasNext()) {
        switch (reader.nextName()) {
          case "id":
            id = UUID.fromString(reader.nextString());
            break;
          case "name":
            name = reader.nextString();
            break;
        }
      }
      reader.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void serialize(DataOutput buffer) {
    try {
      buffer.writeBoolean(id != null);
      if (id != null) {
        buffer.writeLong(id.getMostSignificantBits());
        buffer.writeLong(id.getLeastSignificantBits());
      }
      buffer.writeBoolean(name != null);
      if (name != null) {
        buffer.writeUTF(name);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void deserialize(DataInput buffer) {
    try {
      if (buffer.readBoolean()) {
        id = new UUID(buffer.readLong(), buffer.readLong());
      }
      if (buffer.readBoolean()) {
        name = buffer.readUTF();
      }
      if (id == null && name == null) {
        throw new IOException("Identity is empty");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static Identity fromName(String name) {
    return new Identity(name);
  }

  public static Identity from(UUID id) {
    return new Identity(id);
  }

  public static Identity from(JsonReader reader) {
    Identity identity = new Identity();
    identity.deserialize(reader);
    return identity;
  }

  public static Identity from(DataInput buffer) {
    Identity identity = new Identity();
    identity.deserialize(buffer);
    return identity;
  }
}

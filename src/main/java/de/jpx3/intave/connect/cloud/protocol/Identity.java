package de.jpx3.intave.connect.cloud.protocol;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.annotate.Nullable;
import org.bukkit.entity.Player;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

public class Identity implements JsonSerializable {
  @Nullable
  private UUID uuid;
  @Nullable
  private String name;

  private Identity() {
  }

  public Identity(String name) {
    this.name = name;
  }

  public Identity(UUID id) {
    this.uuid = id;
  }

  public Identity(UUID id, String name) {
    this.uuid = id;
    this.name = name;
  }

  public UUID id() {
    return uuid;
  }

  public String name() {
    return name;
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      if (uuid != null) {
        writer.name("uuid").value(uuid.toString());
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
        while (reader.peek() == JsonToken.NAME) {
          switch (reader.nextName()) {
            case "uuid":
              uuid = UUID.fromString(reader.nextString());
              break;
            case "name":
              name = reader.nextString();
              break;
          }
        }
        if (reader.hasNext()) {
          reader.skipValue();
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
      buffer.writeBoolean(uuid != null);
      if (uuid != null) {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
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
        uuid = new UUID(buffer.readLong(), buffer.readLong());
      }
      if (buffer.readBoolean()) {
        name = buffer.readUTF();
      }
      if (uuid == null && name == null) {
        throw new IOException("Identity is empty");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static Identity from(String name) {
    return new Identity(name);
  }

  public static Identity from(Player player) {
    return new Identity(player.getUniqueId(), player.getName());
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

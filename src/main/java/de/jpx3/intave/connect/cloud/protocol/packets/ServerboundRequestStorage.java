package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Serverbound;

import static de.jpx3.intave.connect.cloud.protocol.Direction.SERVERBOUND;

public final class ServerboundRequestStorage extends JsonPacket<Serverbound> {
  private Identity id;

  public ServerboundRequestStorage() {
    super(SERVERBOUND, "REQUEST_STORAGE", "1");
  }

  public ServerboundRequestStorage(Identity id) {
    super(SERVERBOUND, "REQUEST_STORAGE", "1");
    this.id = id;
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("id");
      id.serialize(writer);
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
            case "id":
              id = Identity.from(reader);
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
}

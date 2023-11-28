package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;

import static com.google.gson.stream.JsonToken.NAME;
import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;

public final class ClientboundDisconnect extends JsonPacket<Clientbound> {
  private String reason;

  public ClientboundDisconnect() {
    super(CLIENTBOUND, "DISCONNECT", "1");
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("reason").value(reason);
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
        while (reader.peek() == NAME) {
          switch (reader.nextName()) {
            case "reason":
              reason = reader.nextString();
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

  public String reason() {
    return reason;
  }
}

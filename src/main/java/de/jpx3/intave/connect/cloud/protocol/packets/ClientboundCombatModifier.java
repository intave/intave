package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;

import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;

public final class ClientboundCombatModifier extends JsonPacket<Clientbound> {
  private Identity id;
  private String modifier;
  private int duration;

  public ClientboundCombatModifier() {
    super(CLIENTBOUND, "REQUEST_ALTERATION", "1");
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("id").value(id.toString());
      writer.name("modifier").value(modifier);
      writer.name("duration").value(duration);
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
            case "modifier":
              modifier = reader.nextString();
              break;
            case "duration":
              duration = reader.nextInt();
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

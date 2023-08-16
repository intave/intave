package de.jpx3.intave.module.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.module.cloud.protocol.Identity;
import de.jpx3.intave.module.cloud.protocol.JsonPacket;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;

import static de.jpx3.intave.module.cloud.protocol.Direction.CLIENTBOUND;

public final class ClientboundCombatModifierPacket extends JsonPacket<Clientbound> {
  private Identity id;
  private String modifier;
  private int duration;

  public ClientboundCombatModifierPacket() {
    super(CLIENTBOUND, "REQUEST_ALTERATION", "1");
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.name("id").value(id.toString());
      writer.name("modifier").value(modifier);
      writer.name("duration").value(duration);
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
      reader.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

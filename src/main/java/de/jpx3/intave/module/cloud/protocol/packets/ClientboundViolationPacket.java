package de.jpx3.intave.module.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.module.cloud.protocol.Identity;
import de.jpx3.intave.module.cloud.protocol.JsonPacket;
import de.jpx3.intave.module.cloud.protocol.listener.Clientbound;

import static de.jpx3.intave.module.cloud.protocol.Direction.CLIENTBOUND;

public final class ClientboundViolationPacket extends JsonPacket<Clientbound> {
  private Identity id;
  private String check;
  private String message;
  private String details;
  private int vl;

  public ClientboundViolationPacket() {
    super(CLIENTBOUND, "VIOLATION", "1");
  }

  public ClientboundViolationPacket(Identity id, String check, String message, String details, int vl) {
    super(CLIENTBOUND, "VIOLATION", "1");
    this.id = id;
    this.check = check;
    this.message = message;
    this.details = details;
    this.vl = vl;
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.name("id");
      id.serialize(writer);
      writer.name("check").value(check);
      writer.name("message").value(message);
      writer.name("details").value(details);
      writer.name("vl").value(vl);
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
          case "check":
            check = reader.nextString();
            break;
          case "message":
            message = reader.nextString();
            break;
          case "details":
            details = reader.nextString();
            break;
          case "vl":
            vl = reader.nextInt();
            break;
        }
      }
      reader.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

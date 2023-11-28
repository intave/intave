package de.jpx3.intave.connect.cloud.protocol.packets;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import de.jpx3.intave.connect.cloud.protocol.Identity;
import de.jpx3.intave.connect.cloud.protocol.JsonPacket;
import de.jpx3.intave.connect.cloud.protocol.listener.Clientbound;

import static com.google.gson.stream.JsonToken.NAME;
import static de.jpx3.intave.connect.cloud.protocol.Direction.CLIENTBOUND;

public final class ClientboundViolation extends JsonPacket<Clientbound> {
  private Identity id;
  private String check;
  private String threshold;
  private String message;
  private String details;
  private int vl;

  public ClientboundViolation() {
    super(CLIENTBOUND, "VIOLATION", "1");
  }

  public ClientboundViolation(Identity id, String check, String threshold, String message, String details, int vl) {
    super(CLIENTBOUND, "VIOLATION", "1");
    this.id = id;
    this.check = check;
    this.threshold = threshold;
    this.message = message;
    this.details = details;
    this.vl = vl;
  }

  @Override
  public void serialize(JsonWriter writer) {
    try {
      writer.beginObject();
      writer.name("id");
      id.serialize(writer);
      writer.name("check").value(check);
      writer.name("threshold").value(threshold);
      writer.name("message").value(message);
      writer.name("details").value(details);
      writer.name("vl").value(vl);

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
            case "id":
              id = Identity.from(reader);
              break;
            case "check":
              check = reader.nextString();
              break;
            case "threshold":
              threshold = reader.nextString();
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
        if (reader.hasNext()) {
          reader.skipValue();
        }
      }
      reader.endObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Identity id() {
    return id;
  }

  public String check() {
    return check;
  }

  public String threshold() {
    return threshold;
  }

  public String message() {
    return message;
  }

  public String details() {
    return details;
  }

  public int vl() {
    return vl;
  }
}

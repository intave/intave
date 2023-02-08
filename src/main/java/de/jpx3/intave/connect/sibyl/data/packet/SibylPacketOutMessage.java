package de.jpx3.intave.connect.sibyl.data.packet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SibylPacketOutMessage extends SibylPacket {
  private String message;

  public SibylPacketOutMessage() {
    super("out-message");
  }

  @Override
  public JsonElement asJsonElement() {
    JsonObject object = new JsonObject();
    object.addProperty("message", message);
    return object;
  }

  @Override
  public void buildFrom(JsonElement element) {
    JsonObject object = element.getAsJsonObject();
    message = object.get("message").getAsString();
  }

  public String message() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

package de.jpx3.intave.connect.sibyl.data.packet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class SibylPacketOutDebug extends SibylPacket {
  private int debugId;
  private String fullMessage;
  private String shortMessage;

  public SibylPacketOutDebug() {
    super("out-debug");
  }

  @Override
  public JsonElement asJsonElement() {
    JsonObject object = new JsonObject();
    object.addProperty("id", debugId);
    object.addProperty("fullMessage", fullMessage);
    object.addProperty("shortMessage", shortMessage);
    return object;
  }

  @Override
  public void buildFrom(JsonElement element) {
    JsonObject object = element.getAsJsonObject();
    debugId = object.get("id").getAsInt();
    fullMessage = object.get("fullMessage").getAsString();
    shortMessage = object.get("shortMessage").getAsString();
  }

  public int debugId() {
    return debugId;
  }

  public String fullMessage() {
    return fullMessage;
  }

  public String shortMessage() {
    return shortMessage;
  }

  public void setDebugId(int debugId) {
    this.debugId = debugId;
  }

  public void setFullMessage(String fullMessage) {
    this.fullMessage = fullMessage;
  }

  public void setShortMessage(String shortMessage) {
    this.shortMessage = shortMessage;
  }
}

package de.jpx3.intave.module.warning;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class ClientData {
  private final String name;
  private final String payload;
  private final String brandcont;
  private final String action;
  private final String content;

  public ClientData(String name, String payload, String brandcont, String action, String content) {
    this.name = name;
    this.payload = payload;
    this.brandcont = brandcont;
    this.action = action;
    this.content = content;
  }

  public String name() {
    return name;
  }

  public String payload() {
    return payload;
  }

  public String brandcont() {
    return brandcont;
  }

  public String action() {
    return action;
  }

  public String content() {
    return content;
  }

  public static ClientData parseFrom(JsonElement jsonElement) {
    JsonObject jsonObject = jsonElement.getAsJsonObject();
    String name = jsonObject.get("name").getAsString();
    String payload = jsonObject.get("payload").getAsString();
    String brandcont = jsonObject.get("brandcont").getAsString();
    String action = jsonObject.get("action").getAsString();
    String content = jsonObject.get("content").getAsString();
    return new ClientData(name, payload, brandcont, action, content);
  }
}

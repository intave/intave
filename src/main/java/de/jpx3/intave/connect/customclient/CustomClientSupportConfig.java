package de.jpx3.intave.connect.customclient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class CustomClientSupportConfig {
  private final boolean legacySneakHeight;

  private CustomClientSupportConfig(boolean legacySneakHeight) {
    this.legacySneakHeight = legacySneakHeight;
  }

  public boolean isLegacySneakHeight() {
    return legacySneakHeight;
  }

  public static CustomClientSupportConfig createDefault() {
    return new CustomClientSupportConfig(false);
  }

  public static CustomClientSupportConfig createFrom(JsonElement jsonElement) {
    JsonObject object = jsonElement.getAsJsonObject();
    boolean read = readBoolean(object, "legacySneakHeight", false);
    return new CustomClientSupportConfig(read);
  }

  private static boolean readBoolean(JsonObject object, String key, boolean def) {
    JsonElement element = object.get(key);
    return element == null ? def : element.getAsBoolean();
  }
}

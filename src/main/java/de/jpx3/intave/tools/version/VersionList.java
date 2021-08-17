package de.jpx3.intave.tools.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.jpx3.intave.resource.CachedResource;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class VersionList {
  private final List<Version> content = new ArrayList<>();
  private final Map<String, Version> contentLookup = new HashMap<>();

  public VersionList() {
  }

  public void setup() {
    CachedResource cachedResource = new CachedResource(
      "license-map",
      "https://intave.de/api/versions.json",
      TimeUnit.DAYS.toMillis(2)
    );
    String raw = String.join("", cachedResource.readLines());
    JsonReader json = new JsonReader(new StringReader(raw));
    json.setLenient(true);
    JsonArray jsonArray = new JsonParser().parse(json).getAsJsonArray();
    for (JsonElement jsonElement : jsonArray) {
      JsonObject jsonObject = jsonElement.getAsJsonObject();
      String name = jsonObject.get("name").getAsString();
      String release = jsonObject.get("release").getAsString();
      String status = jsonObject.get("status").getAsString();
      Version version = new Version(
        name, Long.parseLong(release),
        Version.Status.fromName(status)
      );
      content.add(version);
      contentLookup.put(version.version().toLowerCase(Locale.ROOT), version);
    }
  }

  public Version versionInformation(String version) {
    return contentLookup.get(version.toLowerCase(Locale.ROOT));
  }

  public List<Version> content() {
    return content;
  }
}

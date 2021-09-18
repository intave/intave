package de.jpx3.intave.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.jpx3.intave.resource.CachedResource;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class IntaveVersionList {
  private final List<IntaveVersion> content = new ArrayList<>();
  private final Map<String, IntaveVersion> contentLookup = new HashMap<>();

  public IntaveVersionList() {
  }

  public void setup() {
    CachedResource cachedResource = new CachedResource(
      "versions",
      "https://service.intave.de/versions",
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
      IntaveVersion version = new IntaveVersion(
        name, Long.parseLong(release),
        IntaveVersion.Status.fromName(status)
      );
      content.add(version);
      contentLookup.put(version.version().toLowerCase(Locale.ROOT), version);
    }
  }

  public IntaveVersion versionInformation(String version) {
    return contentLookup.get(version.toLowerCase(Locale.ROOT));
  }

  public List<IntaveVersion> content() {
    return content;
  }
}

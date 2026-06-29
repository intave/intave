package de.jpx3.intave.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.jpx3.intave.IntaveLogger;
import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.resource.Resource;
import de.jpx3.intave.resource.Resources;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class IntaveVersionList {
  private final List<IntaveVersion> content = new ArrayList<>();
  private final Map<String, IntaveVersion> contentLookup = new HashMap<>();

  public IntaveVersionList() {
  }

  public void setup() {
    Resource cachedResource = Resources.localServiceCacheResource(
      "versions",
      "versions",
      TimeUnit.DAYS.toMillis(2)
    );
    try {
      JsonReader json = new JsonReader(new StringReader(cachedResource.readAsString()));
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
    } catch (Exception e) {
      IntaveLogger.logger().warn("Failed to load version list");
    }
  }

  public IntaveVersion current() {
    return versionInformation(IntavePlugin.versionTag());
  }

  public IntaveVersion past(int versions) {
    return content.get(indexOf(current()) - versions);
  }

  private int indexOf(IntaveVersion version) {
    return content.indexOf(version);
  }

  public IntaveVersion versionInformation(String version) {
    return contentLookup.get(version.toLowerCase(Locale.ROOT));
  }

  public List<IntaveVersion> content() {
    return content;
  }
}

package de.jpx3.intave.module.warning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import de.jpx3.intave.resource.CachedResource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public final class ClientDataList {
  private final static CachedResource CACHED_RESOURCE = new CachedResource("clientdata", "https://service.intave.de/clientdata", TimeUnit.DAYS.toMillis(14));
  private final List<ClientData> content;

  public ClientDataList(List<ClientData> content) {
    this.content = content;
  }

  public List<ClientData> content() {
    return content;
  }

  public static ClientDataList generate() {
    if (!CACHED_RESOURCE.available()) {
      return new ClientDataList(new ArrayList<>());
    }
    Scanner scanner = new Scanner(CACHED_RESOURCE.read());
    StringBuilder stringBuilder = new StringBuilder();
    while (scanner.hasNextLine()) {
      stringBuilder.append(scanner.nextLine());
    }
    return new ClientDataList(parseClientData(stringBuilder.toString()));
  }

  private static List<ClientData> parseClientData(String rawJson) {
    List<ClientData> content = new ArrayList<>();
    JsonReader jsonReader = new JsonReader(new StringReader(rawJson));
    jsonReader.setLenient(true);
    JsonArray jsonArray = new JsonParser().parse(jsonReader).getAsJsonArray();
    for (JsonElement jsonElement : jsonArray) {
      content.add(ClientData.parseFrom(jsonElement));
    }
    return content;
  }
}
